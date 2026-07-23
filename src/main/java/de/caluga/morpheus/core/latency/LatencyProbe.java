package de.caluga.morpheus.core.latency;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Latency measurement engine: builds pings, matches answers via inAnswerTo,
 *  sweeps timeouts. Sending and clocks are injected (testable without messaging). */
public class LatencyProbe {

    public enum Mode { INTERVAL, FIXED_RATE, RAMP }

    public record Config(String topic, boolean statusMode, Mode mode, long intervalMs, double rate,
                         double rampStartRate, double rampStepRate, long rampStepMs,
                         double rampAbortLossPct, int senderThreads, int payloadBytes,
                         long timeoutMs, int bufferCapacity) {
        public static Config defaults() {
            return new Config("morpheus_latency", false, Mode.INTERVAL, 1000, 10,
                    10, 10, 10_000, 50.0, 1, 0, 5000, 10_000);
        }
    }

    private static final class Pending {
        final long seq, sentNanos, sentTs;
        volatile boolean answered;
        Pending(long seq, long sentNanos, long sentTs) {
            this.seq = seq; this.sentNanos = sentNanos; this.sentTs = sentTs;
        }
    }

    private final Config cfg;
    private final LatencyStats stats;
    private final Consumer<Msg> sender;
    private final LongSupplier nanoClock;
    private final LongSupplier wallClock;
    private final String runId = Long.toHexString(ThreadLocalRandom.current().nextLong());
    private final Map<MorphiumId, Pending> pending;
    private long seq = 0;

    // scheduling state
    private final java.util.List<Thread> threads = new java.util.ArrayList<>();
    private final java.util.ArrayDeque<Long> sendNanos = new java.util.ArrayDeque<>();
    private final java.util.List<Long> rampStepSeqs = new java.util.ArrayList<>();
    private volatile boolean running = false;
    private volatile boolean rampAborted = false;
    private volatile double currentRampRate;
    private long rampStepStartNanos = Long.MIN_VALUE;
    private long stepStartTimeouts, stepStartCompleted;

    public LatencyProbe(Config cfg, LatencyStats stats, Consumer<Msg> sender,
                        LongSupplier nanoClock, LongSupplier wallClock) {
        this.cfg = cfg;
        this.stats = stats;
        this.sender = sender;
        this.nanoClock = nanoClock;
        this.wallClock = wallClock;
        this.currentRampRate = cfg.rampStartRate();
        this.rampStepStartNanos = nanoClock.getAsLong();
        this.pending = new LinkedHashMap<>() {
            @Override protected boolean removeEldestEntry(Map.Entry<MorphiumId, Pending> e) {
                if (size() <= cfg.bufferCapacity()) return false;
                stats.recordPingCompleted(e.getValue().seq, e.getValue().answered);
                return true;
            }
        };
    }

    public LatencyStats stats() { return stats; }
    public Config config() { return cfg; }

    public synchronized Msg sendPing() {
        long s = ++seq;
        long sentTs = wallClock.getAsLong();
        Msg m;
        if (cfg.statusMode()) {
            m = new Msg(cfg.topic(), "ALL", "ALL", cfg.timeoutMs());
        } else {
            m = new Msg(cfg.topic(), "ping", runId + ":" + s, cfg.timeoutMs());
            Map<String, Object> map = new HashMap<>();
            map.put("seq", s);
            map.put("sentTs", sentTs);
            if (cfg.payloadBytes() > 0) map.put("pad", "x".repeat(cfg.payloadBytes()));
            m.setMapValue(map);
        }
        m.setMsgId(new MorphiumId());
        stats.recordPingSent(s);
        try {
            sender.accept(m);
        } catch (RuntimeException e) {
            stats.recordSendError();
            return null;
        }
        pending.put(m.getMsgId(), new Pending(s, nanoClock.getAsLong(), sentTs));
        if (sendNanos.size() >= 10_000) sendNanos.pollFirst();
        sendNanos.addLast(nanoClock.getAsLong());
        return m;
    }

    public synchronized void handleAnswer(Msg answer) {
        if (answer == null || answer.getInAnswerTo() == null) return;
        Pending p = pending.get(answer.getInAnswerTo());
        if (p == null) return;
        p.answered = true;
        double rttMs = (nanoClock.getAsLong() - p.sentNanos) / 1e6;
        Long recvTs = extractRecvTs(answer);
        Double outbound = null, ret = null;
        if (recvTs != null) {
            outbound = (double) (recvTs - p.sentTs);
            ret = (p.sentTs + rttMs) - recvTs;
        }
        stats.recordAnswer(new LatencySample(p.seq, responderName(answer), answer.getSenderHost(),
                rttMs, outbound, ret, p.sentTs));
    }

    /** Prefers the friendly name the pong responder puts in the answer's mapValue
     *  (via {@code pong --name}) over Morphium's internal sender UUID. */
    private static String responderName(Msg answer) {
        Map<String, Object> map = answer.getMapValue();
        if (map != null && map.get("responder") instanceof String s && !s.isBlank()) return s;
        return answer.getSender();
    }

    public synchronized int sweep() {
        long now = nanoClock.getAsLong();
        int timedOut = 0;
        Iterator<Map.Entry<MorphiumId, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Pending p = it.next().getValue();
            if (now - p.sentNanos > cfg.timeoutMs() * 1_000_000L) {
                stats.recordPingCompleted(p.seq, p.answered);
                if (!p.answered) timedOut++;
                it.remove();
            }
        }
        return timedOut;
    }

    private static Long extractRecvTs(Msg answer) {
        Map<String, Object> map = answer.getMapValue();
        if (map != null && map.get("recvTs") instanceof Number n) return n.longValue();
        return answer.getTimestamp() > 0 ? answer.getTimestamp() : null;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        rampAborted = false;
        currentRampRate = cfg.rampStartRate();
        rampStepStartNanos = nanoClock.getAsLong();
        var snap = stats.snapshot();
        stepStartTimeouts = snap.timeouts();
        stepStartCompleted = snap.completed();
        for (int i = 0; i < Math.max(1, cfg.senderThreads()); i++) {
            Thread t = new Thread(this::senderLoop, "latency-sender-" + i);
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
        Thread sweeper = new Thread(this::sweeperLoop, "latency-sweeper");
        sweeper.setDaemon(true);
        threads.add(sweeper);
        sweeper.start();
    }

    public void stop() {
        running = false;
        java.util.List<Thread> ts;
        synchronized (this) { ts = new java.util.ArrayList<>(threads); threads.clear(); }
        for (Thread t : ts) {
            t.interrupt();
            try { t.join(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    public boolean isRunning() { return running; }
    public boolean rampAborted() { return rampAborted; }
    public synchronized java.util.List<Long> rampStepSeqs() { return java.util.List.copyOf(rampStepSeqs); }

    public double targetRate() {
        return switch (cfg.mode()) {
            case INTERVAL -> 1000.0 / Math.max(1, cfg.intervalMs());
            case FIXED_RATE -> cfg.rate();
            case RAMP -> currentRampRate;
        };
    }

    public synchronized double actualRate() {
        long cutoff = nanoClock.getAsLong() - 10_000_000_000L;
        while (!sendNanos.isEmpty() && sendNanos.peekFirst() < cutoff) sendNanos.pollFirst();
        return sendNanos.size() / 10.0;
    }

    private void senderLoop() {
        int nThreads = Math.max(1, cfg.senderThreads());
        while (running) {
            sendPing();
            long sleepMs = (long) Math.max(1, nThreads * 1000.0 / Math.max(0.001, targetRate()));
            try { Thread.sleep(sleepMs); } catch (InterruptedException e) { return; }
        }
    }

    private void sweeperLoop() {
        while (running) {
            sweep();
            if (cfg.mode() == Mode.RAMP && !rampTick()) { running = false; return; }
            try { Thread.sleep(500); } catch (InterruptedException e) { return; }
        }
    }

    /** Advances ramp state; returns false when the loss threshold aborted the run. */
    synchronized boolean rampTick() {
        if (cfg.mode() != Mode.RAMP) return true;
        if (rampStepStartNanos == Long.MIN_VALUE) rampStepStartNanos = nanoClock.getAsLong();
        if (nanoClock.getAsLong() - rampStepStartNanos < cfg.rampStepMs() * 1_000_000L) return true;
        var snap = stats.snapshot();
        long stepCompleted = snap.completed() - stepStartCompleted;
        long stepTimeouts = snap.timeouts() - stepStartTimeouts;
        double stepLoss = 100.0 * stepTimeouts / Math.max(1, stepCompleted);
        if (stepLoss > cfg.rampAbortLossPct()) {
            rampAborted = true;
            return false;
        }
        currentRampRate += cfg.rampStepRate();
        if (rampStepSeqs.size() < 100) rampStepSeqs.add(seq);
        rampStepStartNanos = nanoClock.getAsLong();
        stepStartCompleted = snap.completed();
        stepStartTimeouts = snap.timeouts();
        return true;
    }
}

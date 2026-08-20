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

    private static final int DEFAULT_PAYLOAD_BYTES = 0;
    private static final long DEFAULT_INTERVAL_MS = 1000;
    private static final double DEFAULT_RATE = 10;
    private static final double DEFAULT_RAMP_START_RATE = 10;
    private static final double DEFAULT_RAMP_STEP_RATE = 10;
    private static final long DEFAULT_RAMP_STEP_MS = 10_000;
    private static final double DEFAULT_RAMP_ABORT_LOSS_PCT = 50.0;
    private static final int DEFAULT_SENDER_THREADS = 1;
    private static final long DEFAULT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_BUFFER_CAPACITY = 10_000;
    private static final long DEFAULT_WARMUP_MS = 0;
    private static final long MIN_VALUE = Long.MIN_VALUE;
    private static final int SEND_NANOS_CAPACITY = 10_000;
    private static final long NANOS_PER_MS = 1_000_000L;
    private static final int JOIN_TIMEOUT_MS = 200;
    private static final double NANOS_PER_SEC = 1e9;
    private static final double MIN_RATE = 0.001;
    private static final long SWEEPER_SLEEP_MS = 500;

    public record Config(String topic, boolean statusMode, Mode mode, long intervalMs, double rate,
                         double rampStartRate, double rampStepRate, long rampStepMs,
                         double rampAbortLossPct, int senderThreads, int payloadBytes,
                         long timeoutMs, int bufferCapacity, long warmupMs, boolean exclusive) {
        /** Non-exclusive pings (the pre-exclusive-profile arity, used by most call sites). */
        public Config(final String topic, final boolean statusMode, final Mode mode,
                      final long intervalMs, final double rate,
                      final double rampStartRate, final double rampStepRate,
                      final long rampStepMs, final double rampAbortLossPct,
                      final int senderThreads, final int payloadBytes,
                      final long timeoutMs, final int bufferCapacity,
                      final long warmupMs) {
            this(topic, statusMode, mode, intervalMs, rate, rampStartRate, rampStepRate,
                    rampStepMs, rampAbortLossPct, senderThreads, payloadBytes,
                    timeoutMs, bufferCapacity, warmupMs, false);
        }

        public Config withExclusive(final boolean v) {
            return new Config(topic, statusMode, mode, intervalMs, rate, rampStartRate, rampStepRate,
                    rampStepMs, rampAbortLossPct, senderThreads, payloadBytes, timeoutMs, bufferCapacity,
                    warmupMs, v);
        }

        public static Config defaults() {
            return new Config("morpheus_latency", false, Mode.INTERVAL, DEFAULT_INTERVAL_MS, DEFAULT_RATE,
                    DEFAULT_RAMP_START_RATE, DEFAULT_RAMP_STEP_RATE, DEFAULT_RAMP_STEP_MS,
                    DEFAULT_RAMP_ABORT_LOSS_PCT, DEFAULT_SENDER_THREADS, DEFAULT_PAYLOAD_BYTES,
                    DEFAULT_TIMEOUT_MS, DEFAULT_BUFFER_CAPACITY, DEFAULT_WARMUP_MS);
        }
    }

    private static final class Pending {
        final long seq, sentNanos, sentTs;
        volatile boolean answered;
        final java.util.Set<String> answeredBy = new java.util.HashSet<>();

        Pending(final long seq, final long sentNanos, final long sentTs) {
            this.seq = seq;
            this.sentNanos = sentNanos;
            this.sentTs = sentTs;
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
    private volatile PingOutcomeListener outcomeListener;
    // Wall clock of the first send; samples whose ping left within warmupMs after this
    // are flagged so the stats can exclude JIT/connection warmup from latency figures.
    private long runStartTs = Long.MIN_VALUE;

    // scheduling state
    private final java.util.List<Thread> threads = new java.util.ArrayList<>();
    private final java.util.ArrayDeque<Long> sendNanos = new java.util.ArrayDeque<>();
    private final java.util.List<Long> rampStepSeqs = new java.util.ArrayList<>();
    private volatile boolean running = false;
    private volatile boolean rampAborted = false;
    private volatile double currentRampRate;
    private long rampStepStartNanos = Long.MIN_VALUE;
    private long stepStartTimeouts, stepStartCompleted;

    public LatencyProbe(final Config cfg, final LatencyStats stats, final Consumer<Msg> sender,
                        final LongSupplier nanoClock, final LongSupplier wallClock) {
        this.cfg = cfg;
        this.stats = stats;
        this.sender = sender;
        this.nanoClock = nanoClock;
        this.wallClock = wallClock;
        this.currentRampRate = cfg.rampStartRate();
        this.rampStepStartNanos = nanoClock.getAsLong();
        this.pending = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<MorphiumId, Pending> e) {
                if (size() <= cfg.bufferCapacity()) {
                    return false;
                }
                stats.recordPingCompleted(e.getValue().seq, e.getValue().answered);
                final PingOutcomeListener l = outcomeListener;
                if (l != null) {
                    l.onPingComplete(e.getValue().seq, e.getValue().sentTs,
                            java.util.Set.copyOf(e.getValue().answeredBy));
                }
                return true;
            }
        };
    }

    public LatencyStats stats() {
        return stats;
    }

    public Config config() {
        return cfg;
    }

    public void setOutcomeListener(final PingOutcomeListener l) {
        this.outcomeListener = l;
    }

    public Msg sendPing() {
        final Msg m;
        final long s;
        final long sentTs;
        synchronized (this) {
            s = ++seq;
            sentTs = wallClock.getAsLong();
            if (runStartTs == MIN_VALUE) {
                runStartTs = sentTs;
            }
            if (cfg.statusMode()) {
                m = new Msg(cfg.topic(), "ALL", "ALL", cfg.timeoutMs());
            } else {
                m = new Msg(cfg.topic(), "ping", runId + ":" + s, cfg.timeoutMs());
                // Exclusive request/reply profile: the responder side then runs the full
                // lock/claim path, matching how production sync request/reply rides the bus —
                // measurably slower than the broadcast path, which is the point of measuring it.
                if (cfg.exclusive()) {
                    m.setExclusive(true);
                }
                final Map<String, Object> map = new HashMap<>();
                map.put("seq", s);
                map.put("sentTs", sentTs);
                if (cfg.payloadBytes() > 0) {
                    map.put("pad", "x".repeat(cfg.payloadBytes()));
                }
                m.setMapValue(map);
            }
            m.setMsgId(new MorphiumId());
            stats.recordPingSent(s);
            if (sendNanos.size() >= SEND_NANOS_CAPACITY) {
                sendNanos.pollFirst();
            }
            sendNanos.addLast(nanoClock.getAsLong());
        }
        // Clock starts here, before the (possibly slow) send call, and the Pending entry is inserted
        // before sender.accept runs so an instantly-arriving answer (msgId is already assigned) finds
        // it via the synchronized handleAnswer. This intentionally does NOT hold the probe monitor
        // across the send itself, so concurrent sender threads don't serialize on network/publish cost.
        final long sendStartNanos = nanoClock.getAsLong();
        synchronized (this) {
            pending.put(m.getMsgId(), new Pending(s, sendStartNanos, sentTs));
        }
        try {
            sender.accept(m);
        } catch (final RuntimeException e) {
            synchronized (this) {
                pending.remove(m.getMsgId());
            }
            stats.recordSendError();
            final PingOutcomeListener l = outcomeListener;
            if (l != null) {
                l.onSendError(sentTs);
            }
            return null;
        }
        return m;
    }

    public synchronized void handleAnswer(final Msg answer) {
        if (answer == null || answer.getInAnswerTo() == null) {
            return;
        }
        final Pending p = pending.get(answer.getInAnswerTo());
        if (p == null) {
            return;
        }
        p.answered = true;
        final double rttMs = (nanoClock.getAsLong() - p.sentNanos) / 1e6;
        final Long recvTs = extractRecvTs(answer);
        Double outbound = null, ret = null;
        if (recvTs != null) {
            outbound = (double) (recvTs - p.sentTs);
            ret = (p.sentTs + rttMs) - recvTs;
        }
        final boolean warmup = cfg.warmupMs() > 0 && p.sentTs - runStartTs < cfg.warmupMs();
        final LatencySample sample = new LatencySample(p.seq, responderName(answer), answer.getSenderHost(),
                rttMs, outbound, ret, p.sentTs, warmup);
        p.answeredBy.add(sample.responderKey());
        stats.recordAnswer(sample);
    }

    /** Prefers the friendly name the pong responder puts in the answer's mapValue
     *  (via {@code pong --name}) over Morphium's internal sender UUID. */
    private static String responderName(final Msg answer) {
        final Map<String, Object> map = answer.getMapValue();
        if (map != null && map.get("responder") instanceof String s && !s.isBlank()) {
            return s;
        }
        return answer.getSender();
    }

    public synchronized int sweep() {
        final long now = nanoClock.getAsLong();
        int timedOut = 0;
        final Iterator<Map.Entry<MorphiumId, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            final Pending p = it.next().getValue();
            if (now - p.sentNanos > cfg.timeoutMs() * NANOS_PER_MS) {
                stats.recordPingCompleted(p.seq, p.answered);
                final PingOutcomeListener l = outcomeListener;
                if (l != null) {
                    l.onPingComplete(p.seq, p.sentTs, java.util.Set.copyOf(p.answeredBy));
                }
                if (!p.answered) {
                    timedOut++;
                }
                it.remove();
            }
        }
        return timedOut;
    }

    private static Long extractRecvTs(final Msg answer) {
        final Map<String, Object> map = answer.getMapValue();
        if (map != null && map.get("recvTs") instanceof Number n) {
            return n.longValue();
        }
        return answer.getTimestamp() > 0 ? answer.getTimestamp() : null;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        rampAborted = false;
        currentRampRate = cfg.rampStartRate();
        rampStepStartNanos = nanoClock.getAsLong();
        final var snap = stats.snapshot();
        stepStartTimeouts = snap.timeouts();
        stepStartCompleted = snap.completed();
        for (int i = 0; i < Math.max(1, cfg.senderThreads()); i++) {
            final int threadIdx = i;
            final Thread t = new Thread(() -> senderLoop(threadIdx), "latency-sender-" + i);
            t.setDaemon(true);
            threads.add(t);
            t.start();
        }
        final Thread sweeper = new Thread(this::sweeperLoop, "latency-sweeper");
        sweeper.setDaemon(true);
        threads.add(sweeper);
        sweeper.start();
    }

    public void stop() {
        running = false;
        final java.util.List<Thread> ts;
        synchronized (this) {
            ts = new java.util.ArrayList<>(threads);
            threads.clear();
        }
        for (final Thread t : ts) {
            t.interrupt();
            try {
                t.join(JOIN_TIMEOUT_MS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean rampAborted() {
        return rampAborted;
    }

    public synchronized java.util.List<Long> rampStepSeqs() {
        return java.util.List.copyOf(rampStepSeqs);
    }

    public double targetRate() {
        return switch (cfg.mode()) {
            case INTERVAL -> 1000.0 / Math.max(1, cfg.intervalMs());
            case FIXED_RATE -> cfg.rate();
            case RAMP -> currentRampRate;
        };
    }

    public synchronized double actualRate() {
        final long cutoff = nanoClock.getAsLong() - 10_000_000_000L;
        while (!sendNanos.isEmpty() && sendNanos.peekFirst() < cutoff) {
            sendNanos.pollFirst();
        }
        return sendNanos.size() / 10.0;
    }

    /** Fixed-rate sender: schedules sends on absolute timestamps (next += period) so the
     *  configured rate is met regardless of how long the (blocking) send call takes. The old
     *  sleep-after-send pacing added the full send duration to every cycle, silently
     *  undershooting the target rate and hiding latency via coordinated omission. */
    private void senderLoop(final int threadIdx) {
        final int nThreads = Math.max(1, cfg.senderThreads());
        long period = periodNanos(nThreads);
        // stagger the threads across one period so their sends interleave instead of bursting
        long next = nanoClock.getAsLong() + period * threadIdx / nThreads;
        while (running) {
            final long now = nanoClock.getAsLong();
            if (now < next) {
                final long wait = next - now;
                try {
                    Thread.sleep(wait / NANOS_PER_MS, (int) (wait % NANOS_PER_MS));
                } catch (final InterruptedException e) {
                    return;
                }
                continue;   // re-check running and the clock after every sleep
            }
            sendPing();
            period = periodNanos(nThreads);   // rate may have changed (RAMP mode)
            next += period;
            // Fell behind by more than a full period (send outlasted the schedule): rebase
            // instead of firing a catch-up burst that would distort the latency measurement.
            if (next < nanoClock.getAsLong() - period) {
                next = nanoClock.getAsLong();
            }
        }
    }

    private long periodNanos(final int nThreads) {
        return (long) (nThreads * NANOS_PER_SEC / Math.max(MIN_RATE, targetRate()));
    }

    private void sweeperLoop() {
        while (running) {
            sweep();
            // Set running=false directly rather than calling stop(): stop() joins every tracked
            // thread including this one, and a thread cannot join itself.
            if (cfg.mode() == Mode.RAMP && !rampTick()) {
                running = false;
                return;
            }
            try {
                Thread.sleep(SWEEPER_SLEEP_MS);
            } catch (final InterruptedException e) {
                return;
            }
        }
    }

    /** Advances ramp state; returns false when the loss threshold aborted the run. */
    synchronized boolean rampTick() {
        if (cfg.mode() != Mode.RAMP) {
            return true;
        }
        if (nanoClock.getAsLong() - rampStepStartNanos < cfg.rampStepMs() * NANOS_PER_MS) {
            return true;
        }
        final var snap = stats.snapshot();
        final long stepCompleted = snap.completed() - stepStartCompleted;
        final long stepTimeouts = snap.timeouts() - stepStartTimeouts;
        final double stepLoss = 100.0 * stepTimeouts / Math.max(1, stepCompleted);
        if (stepLoss > cfg.rampAbortLossPct()) {
            rampAborted = true;
            return false;
        }
        currentRampRate += cfg.rampStepRate();
        if (rampStepSeqs.size() < 100) {
            rampStepSeqs.add(seq);
        }
        rampStepStartNanos = nanoClock.getAsLong();
        stepStartCompleted = snap.completed();
        stepStartTimeouts = snap.timeouts();
        return true;
    }
}

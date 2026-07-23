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

    public LatencyProbe(Config cfg, LatencyStats stats, Consumer<Msg> sender,
                        LongSupplier nanoClock, LongSupplier wallClock) {
        this.cfg = cfg;
        this.stats = stats;
        this.sender = sender;
        this.nanoClock = nanoClock;
        this.wallClock = wallClock;
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
        stats.recordAnswer(new LatencySample(p.seq, answer.getSender(), answer.getSenderHost(),
                rttMs, outbound, ret, p.sentTs));
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
}

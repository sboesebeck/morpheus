package de.caluga.morpheus.core.latency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded rolling latency statistics: lifetime counters per responder plus
 *  window-based percentiles/jitter/histogram over a sample ring. Thread-safe. */
public class LatencyStats {

    public static final double[] BUCKET_BOUNDS = {1, 2, 5, 10, 20, 50, 100, 200, 500, 1000};

    private final int capacity;
    private final ArrayDeque<LatencySample> ring = new ArrayDeque<>();
    private final ArrayDeque<Long> timeoutSeqs = new ArrayDeque<>();
    private final Map<String, Lifetime> responders = new LinkedHashMap<>();
    private long pingsSent, completed, timeouts, sendErrors, lastSeq;

    private static final class Lifetime {
        long count, firstSeenSeq;
        // latency aggregates track non-warmup samples only; count keeps counting everything
        // so loss% (delivery) stays correct even when a warmup window is configured
        long latCount;
        double last, min = Double.MAX_VALUE, max, sum;
    }

    public LatencyStats(int capacity) { this.capacity = Math.max(1, capacity); }

    public synchronized void recordPingSent(long seq) { pingsSent++; lastSeq = Math.max(lastSeq, seq); }

    public synchronized void recordSendError() { sendErrors++; }

    public synchronized void recordAnswer(LatencySample s) {
        if (ring.size() >= capacity) ring.pollFirst();
        ring.addLast(s);
        Lifetime lt = responders.computeIfAbsent(s.responderKey(), k -> {
            Lifetime l = new Lifetime();
            l.firstSeenSeq = s.seq();
            return l;
        });
        lt.count++;
        if (!s.warmup()) {
            lt.latCount++;
            lt.last = s.rttMs();
            lt.min = Math.min(lt.min, s.rttMs());
            lt.max = Math.max(lt.max, s.rttMs());
            lt.sum += s.rttMs();
        }
    }

    public synchronized void recordPingCompleted(long seq, boolean answered) {
        completed++;
        if (!answered) {
            timeouts++;
            if (timeoutSeqs.size() >= capacity) timeoutSeqs.pollFirst();
            timeoutSeqs.addLast(seq);
        }
    }

    public synchronized Snapshot snapshot() {
        Map<String, List<LatencySample>> window = new HashMap<>();
        for (LatencySample s : ring) {
            if (s.warmup()) continue;
            window.computeIfAbsent(s.responderKey(), k -> new ArrayList<>()).add(s);
        }

        List<ResponderStats> out = new ArrayList<>();
        for (var e : responders.entrySet()) {
            Lifetime lt = e.getValue();
            List<LatencySample> w = window.getOrDefault(e.getKey(), List.of());
            double[] rtts = w.stream().mapToDouble(LatencySample::rttMs).sorted().toArray();
            double jitter = 0;
            for (int i = 1; i < w.size(); i++) jitter += Math.abs(w.get(i).rttMs() - w.get(i - 1).rttMs());
            jitter = w.size() > 1 ? jitter / (w.size() - 1) : 0;
            double expected = Math.max(1, lastSeq - lt.firstSeenSeq + 1);
            double loss = 100.0 * Math.max(0, 1 - (double) lt.count / expected);
            out.add(new ResponderStats(e.getKey(), lt.count, loss, lt.last,
                    lt.latCount == 0 ? 0 : lt.min, lt.latCount == 0 ? 0 : lt.sum / lt.latCount, lt.max,
                    pct(rtts, 50), pct(rtts, 90), pct(rtts, 99), jitter,
                    mean(w, true), mean(w, false)));
        }

        long[] histogram = new long[BUCKET_BOUNDS.length + 1];
        for (LatencySample s : ring) {
            if (!s.warmup()) histogram[bucket(s.rttMs())]++;
        }

        double loss = completed == 0 ? 0 : 100.0 * timeouts / completed;
        return new Snapshot(pingsSent, completed, timeouts, sendErrors, loss,
                out, histogram, List.copyOf(ring), List.copyOf(timeoutSeqs));
    }

    private static int bucket(double rtt) {
        for (int i = 0; i < BUCKET_BOUNDS.length; i++) if (rtt <= BUCKET_BOUNDS[i]) return i;
        return BUCKET_BOUNDS.length;
    }

    private static double pct(double[] sorted, int p) {
        if (sorted.length == 0) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, idx))];
    }

    private static Double mean(List<LatencySample> w, boolean outbound) {
        double sum = 0; int n = 0;
        for (LatencySample s : w) {
            Double v = outbound ? s.outboundMs() : s.returnMs();
            if (v != null) { sum += v; n++; }
        }
        return n == 0 ? null : sum / n;
    }

    public record Snapshot(long pingsSent, long completed, long timeouts, long sendErrors,
                           double lossPct, List<ResponderStats> responders, long[] histogram,
                           List<LatencySample> samples, List<Long> timeoutSeqs) {}

    public record ResponderStats(String key, long count, double lossPct, double lastMs,
                                 double minMs, double avgMs, double maxMs,
                                 double p50, double p90, double p99, double jitterMs,
                                 Double outboundMs, Double returnMs) {}
}

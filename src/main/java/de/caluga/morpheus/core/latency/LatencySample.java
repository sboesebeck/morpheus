package de.caluga.morpheus.core.latency;

/** One answered ping observation. outbound/return are approximate (two clocks) and nullable.
 *  warmup samples (ping sent within the configured warmup window) are kept in the series
 *  but excluded from latency aggregates. */
public record LatencySample(long seq, String sender, String senderHost,
                            double rttMs, Double outboundMs, Double returnMs, long sentTs,
                            boolean warmup) {

    public LatencySample(final long seq, final String sender, final String senderHost,
                         final double rttMs, final Double outboundMs, final Double returnMs,
                         final long sentTs) {
        this(seq, sender, senderHost, rttMs, outboundMs, returnMs, sentTs, false);
    }

    public String responderKey() {
        return (sender == null ? "?" : sender) + "/" + (senderHost == null ? "?" : senderHost);
    }
}

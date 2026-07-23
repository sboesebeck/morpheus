package de.caluga.morpheus.core.latency;

/** One answered ping observation. outbound/return are approximate (two clocks) and nullable. */
public record LatencySample(long seq, String sender, String senderHost,
                            double rttMs, Double outboundMs, Double returnMs, long sentTs) {

    public String responderKey() {
        return (sender == null ? "?" : sender) + "/" + (senderHost == null ? "?" : senderHost);
    }
}

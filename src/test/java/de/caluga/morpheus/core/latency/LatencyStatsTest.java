package de.caluga.morpheus.core.latency;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class LatencyStatsTest {

    private LatencySample s(long seq, String sender, double rtt) {
        return new LatencySample(seq, sender, "hostA", rtt, null, null, 1000 + seq);
    }

    @Test
    void responderKeyNullSafe() {
        assertEquals("a/hostA", s(1, "a", 1.0).responderKey());
        assertEquals("?/?", new LatencySample(1, null, null, 1.0, null, null, 0).responderKey());
    }

    @Test
    void basicStatsPerResponder() {
        LatencyStats st = new LatencyStats(100);
        for (long i = 1; i <= 4; i++) st.recordPingSent(i);
        st.recordAnswer(s(1, "a", 2.0));
        st.recordAnswer(s(2, "a", 4.0));
        st.recordAnswer(s(3, "a", 6.0));
        st.recordAnswer(s(4, "a", 8.0));
        var r = st.snapshot().responders().get(0);
        assertEquals("a/hostA", r.key());
        assertEquals(4, r.count());
        assertEquals(8.0, r.lastMs(), 1e-9);
        assertEquals(2.0, r.minMs(), 1e-9);
        assertEquals(5.0, r.avgMs(), 1e-9);
        assertEquals(8.0, r.maxMs(), 1e-9);
        assertEquals(2.0, r.jitterMs(), 1e-9);   // diffs 2,2,2
        assertEquals(0.0, r.lossPct(), 1e-9);
    }

    @Test
    void percentilesNearestRank() {
        LatencyStats st = new LatencyStats(1000);
        for (long i = 1; i <= 100; i++) { st.recordPingSent(i); st.recordAnswer(s(i, "a", i)); }
        var r = st.snapshot().responders().get(0);
        assertEquals(50.0, r.p50(), 1e-9);
        assertEquals(90.0, r.p90(), 1e-9);
        assertEquals(99.0, r.p99(), 1e-9);
    }

    @Test
    void ringEvictsEldest() {
        LatencyStats st = new LatencyStats(3);
        for (long i = 1; i <= 5; i++) { st.recordPingSent(i); st.recordAnswer(s(i, "a", i)); }
        var snap = st.snapshot();
        assertEquals(3, snap.samples().size());
        assertEquals(3, snap.samples().get(0).seq());       // 1 and 2 evicted
        assertEquals(1.0, snap.responders().get(0).minMs(), 1e-9); // lifetime min survives eviction
        assertEquals(4.0, snap.responders().get(0).p50(), 1e-9);   // window p50 over {3,4,5} -> nearest rank ceil(0.5*3)=2 -> sorted[1]=4
    }

    @Test
    void lossAggregateAndTimeoutSeqs() {
        LatencyStats st = new LatencyStats(100);
        for (long i = 1; i <= 4; i++) st.recordPingSent(i);
        st.recordAnswer(s(1, "a", 2.0));
        st.recordPingCompleted(1, true);
        st.recordPingCompleted(2, false);
        st.recordPingCompleted(3, false);
        var snap = st.snapshot();
        assertEquals(3, snap.completed());
        assertEquals(2, snap.timeouts());
        assertEquals(100.0 * 2 / 3, snap.lossPct(), 1e-9);
        assertEquals(List.of(2L, 3L), snap.timeoutSeqs());
    }

    @Test
    void responderLossFromMissedSeqs() {
        LatencyStats st = new LatencyStats(100);
        for (long i = 1; i <= 10; i++) st.recordPingSent(i);
        // responder first seen at seq 1, answered 5 of 10 -> 50% loss
        for (long i : new long[]{1, 3, 5, 7, 9}) st.recordAnswer(s(i, "a", 1.0));
        assertEquals(50.0, st.snapshot().responders().get(0).lossPct(), 1e-9);
    }

    @Test
    void histogramBuckets() {
        LatencyStats st = new LatencyStats(100);
        double[] rtts = {0.5, 1.5, 3.0, 7.0, 15.0, 30.0, 70.0, 150.0, 300.0, 700.0, 5000.0};
        long seq = 0;
        for (double r : rtts) { st.recordPingSent(++seq); st.recordAnswer(s(seq, "a", r)); }
        long[] h = st.snapshot().histogram();
        assertEquals(11, h.length);
        for (long c : h) assertEquals(1, c);
    }

    @Test
    void outboundReturnMeansNullWhenAbsent() {
        LatencyStats st = new LatencyStats(100);
        st.recordPingSent(1);
        st.recordAnswer(new LatencySample(1, "a", "h", 4.0, 2.5, 1.5, 0));
        st.recordPingSent(2);
        st.recordAnswer(new LatencySample(2, "a", "h", 4.0, null, null, 0));
        var r = st.snapshot().responders().get(0);
        assertEquals(2.5, r.outboundMs(), 1e-9);
        assertEquals(1.5, r.returnMs(), 1e-9);

        LatencyStats st2 = new LatencyStats(100);
        st2.recordPingSent(1);
        st2.recordAnswer(new LatencySample(1, "b", "h", 4.0, null, null, 0));
        assertNull(st2.snapshot().responders().get(0).outboundMs());
    }

    @Test
    void sendErrorsCounted() {
        LatencyStats st = new LatencyStats(10);
        st.recordSendError();
        st.recordSendError();
        assertEquals(2, st.snapshot().sendErrors());
    }
}

package de.caluga.morpheus.core.latency;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class LatencyProbeTest {

    private final List<Msg> sent = new ArrayList<>();
    private final AtomicLong nanos = new AtomicLong(0);
    private final AtomicLong wall = new AtomicLong(100_000);

    private LatencyProbe probe(LatencyProbe.Config cfg) {
        return new LatencyProbe(cfg, new LatencyStats(cfg.bufferCapacity()),
                sent::add, nanos::get, wall::get);
    }

    private Msg answerTo(Msg ping, String sender, Long recvTs) {
        Msg a = new Msg(ping.getTopic(), "pong", "v", 1000);
        a.setMsgId(new MorphiumId());
        a.setInAnswerTo(ping.getMsgId());
        a.setSender(sender);
        a.setSenderHost("hostX");
        a.setTimestamp(0);
        if (recvTs != null) a.setMapValue(Map.of("recvTs", recvTs));
        return a;
    }

    @Test
    void pingCarriesSeqSentTsAndTtl() {
        LatencyProbe p = probe(LatencyProbe.Config.defaults());
        Msg m = p.sendPing();
        assertEquals(1, sent.size());
        assertEquals("morpheus_latency", m.getTopic());
        assertEquals(1L, ((Number) m.getMapValue().get("seq")).longValue());
        assertEquals(100_000L, ((Number) m.getMapValue().get("sentTs")).longValue());
        assertNull(m.getMapValue().get("pad"));
        assertEquals(5000, m.getTtl());
    }

    @Test
    void payloadPaddingApplied() {
        var cfg = LatencyProbe.Config.defaults();
        cfg = new LatencyProbe.Config(cfg.topic(), false, cfg.mode(), cfg.intervalMs(), cfg.rate(),
                cfg.rampStartRate(), cfg.rampStepRate(), cfg.rampStepMs(), cfg.rampAbortLossPct(),
                cfg.senderThreads(), 1024, cfg.timeoutMs(), cfg.bufferCapacity());
        Msg m = probe(cfg).sendPing();
        assertEquals(1024, ((String) m.getMapValue().get("pad")).length());
    }

    @Test
    void answerProducesRttSampleSingleClock() {
        LatencyProbe p = probe(LatencyProbe.Config.defaults());
        Msg ping = p.sendPing();
        nanos.addAndGet(3_000_000);            // 3ms later
        p.handleAnswer(answerTo(ping, "pong-1", null));
        var snap = p.stats().snapshot();
        assertEquals(1, snap.samples().size());
        assertEquals(3.0, snap.samples().get(0).rttMs(), 1e-6);
        assertEquals("pong-1/hostX", snap.samples().get(0).responderKey());
    }

    @Test
    void pathSplitWithRecvTs() {
        LatencyProbe p = probe(LatencyProbe.Config.defaults());
        Msg ping = p.sendPing();               // sentTs = 100_000
        nanos.addAndGet(4_000_000);            // rtt 4ms
        p.handleAnswer(answerTo(ping, "pong-1", 100_003L)); // outbound ~3ms
        var s = p.stats().snapshot().samples().get(0);
        assertEquals(3.0, s.outboundMs(), 1e-6);
        assertEquals(1.0, s.returnMs(), 1e-6); // (100000+4) - 100003
    }

    @Test
    void fallbackToAnswerTimestamp() {
        LatencyProbe p = probe(LatencyProbe.Config.defaults());
        Msg ping = p.sendPing();
        nanos.addAndGet(10_000_000);           // 10ms
        Msg a = answerTo(ping, "app-1", null);
        a.setTimestamp(100_006);               // morphium-set creation ts
        p.handleAnswer(a);
        var s = p.stats().snapshot().samples().get(0);
        assertEquals(6.0, s.outboundMs(), 1e-6);
        assertEquals(4.0, s.returnMs(), 1e-6);
    }

    @Test
    void unknownAnswerIgnored() {
        LatencyProbe p = probe(LatencyProbe.Config.defaults());
        p.sendPing();
        Msg stray = new Msg("morpheus_latency", "pong", "v", 1000);
        stray.setMsgId(new MorphiumId());
        stray.setInAnswerTo(new MorphiumId());
        p.handleAnswer(stray);
        p.handleAnswer(new Msg("morpheus_latency", "x", "v", 1000)); // no inAnswerTo
        assertEquals(0, p.stats().snapshot().samples().size());
    }

    @Test
    void sweepTimesOutUnansweredOnly() {
        LatencyProbe p = probe(LatencyProbe.Config.defaults());
        Msg ping1 = p.sendPing();
        Msg ping2 = p.sendPing();
        nanos.addAndGet(1_000_000);
        p.handleAnswer(answerTo(ping1, "pong-1", null));
        nanos.addAndGet(5_000_000_000L);       // > 5s timeout
        assertEquals(1, p.sweep());            // only ping2 counts as timeout
        var snap = p.stats().snapshot();
        assertEquals(2, snap.completed());
        assertEquals(1, snap.timeouts());
        // second answer to ping1 after sweep is ignored (pending removed)
        p.handleAnswer(answerTo(ping1, "pong-2", null));
        assertEquals(1, p.stats().snapshot().samples().size());
    }

    @Test
    void pendingOverflowEvictsAsTimeout() {
        var d = LatencyProbe.Config.defaults();
        var cfg = new LatencyProbe.Config(d.topic(), false, d.mode(), d.intervalMs(), d.rate(),
                d.rampStartRate(), d.rampStepRate(), d.rampStepMs(), d.rampAbortLossPct(),
                d.senderThreads(), 0, d.timeoutMs(), 3);
        LatencyProbe p = probe(cfg);
        for (int i = 0; i < 5; i++) p.sendPing();
        var snap = p.stats().snapshot();
        assertEquals(2, snap.completed());     // two evicted
        assertEquals(2, snap.timeouts());
    }

    @Test
    void sendErrorCountedNotThrown() {
        LatencyProbe p = new LatencyProbe(LatencyProbe.Config.defaults(), new LatencyStats(10),
                m -> { throw new RuntimeException("boom"); }, nanos::get, wall::get);
        assertNull(p.sendPing());
        assertEquals(1, p.stats().snapshot().sendErrors());
        assertEquals(1, p.stats().snapshot().pingsSent());
    }

    @Test
    void statusModeUsesAllMarkers() {
        var d = LatencyProbe.Config.defaults();
        var cfg = new LatencyProbe.Config("msg_status_info", true, d.mode(), d.intervalMs(), d.rate(),
                d.rampStartRate(), d.rampStepRate(), d.rampStepMs(), d.rampAbortLossPct(),
                d.senderThreads(), 0, d.timeoutMs(), d.bufferCapacity());
        Msg m = probe(cfg).sendPing();
        assertEquals("msg_status_info", m.getTopic());
        assertEquals("ALL", m.getMsg());
        assertEquals("ALL", m.getValue());
        assertNull(m.getMapValue());
    }
}

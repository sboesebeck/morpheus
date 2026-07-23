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
        return answerTo(ping, sender, recvTs, null);
    }

    private Msg answerTo(Msg ping, String sender, Long recvTs, String responderName) {
        Msg a = new Msg(ping.getTopic(), "pong", "v", 1000);
        a.setMsgId(new MorphiumId());
        a.setInAnswerTo(ping.getMsgId());
        a.setSender(sender);
        a.setSenderHost("hostX");
        a.setTimestamp(0);
        if (recvTs != null || responderName != null) {
            Map<String, Object> map = new java.util.HashMap<>();
            if (recvTs != null) map.put("recvTs", recvTs);
            if (responderName != null) map.put("responder", responderName);
            a.setMapValue(map);
        }
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
    void answerUsesFriendlyResponderNameWhenPresent() {
        LatencyProbe p = probe(LatencyProbe.Config.defaults());
        Msg ping = p.sendPing();
        nanos.addAndGet(3_000_000);            // 3ms later
        p.handleAnswer(answerTo(ping, "internal-uuid-sender", 100_003L, "pong-nice"));
        var s = p.stats().snapshot().samples().get(0);
        assertTrue(s.responderKey().startsWith("pong-nice/"),
                "expected friendly name to win over sender UUID, got " + s.responderKey());
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

    @org.junit.jupiter.api.Nested
    class Scheduling {

        @Test
        void targetRateFollowsMode() {
            var d = LatencyProbe.Config.defaults();
            assertEquals(1.0, probe(d).targetRate(), 1e-9); // 1000ms interval
            var fixed = new LatencyProbe.Config(d.topic(), false, LatencyProbe.Mode.FIXED_RATE,
                    d.intervalMs(), 250, d.rampStartRate(), d.rampStepRate(), d.rampStepMs(),
                    d.rampAbortLossPct(), 1, 0, d.timeoutMs(), d.bufferCapacity());
            assertEquals(250.0, probe(fixed).targetRate(), 1e-9);
        }

        @Test
        void rampStepsIncreaseRateAndRecordSeqs() {
            var d = LatencyProbe.Config.defaults();
            var cfg = new LatencyProbe.Config(d.topic(), false, LatencyProbe.Mode.RAMP,
                    d.intervalMs(), d.rate(), 50, 25, 10_000, 50.0, 1, 0, d.timeoutMs(), d.bufferCapacity());
            LatencyProbe p = probe(cfg);
            assertEquals(50.0, p.targetRate(), 1e-9);
            nanos.addAndGet(10_000_000_000L + 1);   // one step elapsed
            assertTrue(p.rampTick());
            assertEquals(75.0, p.targetRate(), 1e-9);
            assertEquals(1, p.rampStepSeqs().size());
        }

        @Test
        void rampAbortsOnLoss() {
            var d = LatencyProbe.Config.defaults();
            var cfg = new LatencyProbe.Config(d.topic(), false, LatencyProbe.Mode.RAMP,
                    d.intervalMs(), d.rate(), 50, 25, 10_000, 50.0, 1, 0, d.timeoutMs(), d.bufferCapacity());
            LatencyProbe p = probe(cfg);
            // simulate a step with 100% loss
            p.sendPing(); p.sendPing();
            nanos.addAndGet(6_000_000_000L);        // both time out (5s timeout)
            p.sweep();
            nanos.addAndGet(5_000_000_000L);        // step boundary passed
            assertFalse(p.rampTick());
            assertTrue(p.rampAborted());
        }

        @Test
        void startStopLifecycle() throws Exception {
            var d = LatencyProbe.Config.defaults();
            var cfg = new LatencyProbe.Config(d.topic(), false, LatencyProbe.Mode.FIXED_RATE,
                    d.intervalMs(), 200, d.rampStartRate(), d.rampStepRate(), d.rampStepMs(),
                    d.rampAbortLossPct(), 2, 0, d.timeoutMs(), d.bufferCapacity());
            LatencyProbe p = new LatencyProbe(cfg, new LatencyStats(1000), sent::add,
                    System::nanoTime, System::currentTimeMillis);
            p.start();
            assertTrue(p.isRunning());
            Thread.sleep(300);
            p.stop();
            assertFalse(p.isRunning());
            int after = sent.size();
            assertTrue(after > 10, "should have sent a bunch at 200/s, got " + after);
            Thread.sleep(200);
            assertEquals(after, sent.size(), "no sends after stop");
            p.stop(); // idempotent
        }

        @Test
        void actualRateComputedFromRecentSends() {
            LatencyProbe p = probe(LatencyProbe.Config.defaults());
            for (int i = 0; i < 20; i++) p.sendPing();  // all at nanos=0 within 10s window
            assertEquals(2.0, p.actualRate(), 1e-9);     // 20 sends / 10s
        }
    }
}

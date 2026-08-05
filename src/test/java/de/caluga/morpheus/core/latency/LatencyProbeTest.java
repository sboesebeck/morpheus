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
                cfg.senderThreads(), 1024, cfg.timeoutMs(), cfg.bufferCapacity(), 0);
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
        p.sendPing();
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
                d.senderThreads(), 0, d.timeoutMs(), 3, 0);
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
    void sendErrorRemovesPendingSoStrayAnswerIsIgnored() {
        final Msg[] captured = new Msg[1];
        LatencyProbe p = new LatencyProbe(LatencyProbe.Config.defaults(), new LatencyStats(10),
                m -> { captured[0] = m; throw new RuntimeException("boom"); }, nanos::get, wall::get);
        assertNull(p.sendPing());
        assertEquals(1, p.stats().snapshot().sendErrors());
        assertNotNull(captured[0], "sender must still receive the msg before throwing");
        Msg stray = new Msg(captured[0].getTopic(), "pong", "v", 1000);
        stray.setMsgId(new MorphiumId());
        stray.setInAnswerTo(captured[0].getMsgId());
        p.handleAnswer(stray);
        assertEquals(0, p.stats().snapshot().samples().size(), "pending entry for a failed send must be gone");
    }

    /** RTT must be measured from just before the send call, not after it returns — otherwise the
     *  publish/network cost of sender.accept is excluded from the RTT and returnMs can go negative
     *  even with perfectly synced clocks. */
    @Test
    void rttClockStartsBeforeSendNotAfter() {
        LatencyProbe p = new LatencyProbe(LatencyProbe.Config.defaults(), new LatencyStats(10),
                m -> { sent.add(m); nanos.addAndGet(5_000_000); /* 5ms spent "sending" */ },
                nanos::get, wall::get);
        Msg ping = p.sendPing();
        p.handleAnswer(answerTo(ping, "pong-1", null)); // answer arrives "instantly" after sendPing returns
        var s = p.stats().snapshot().samples().get(0);
        assertTrue(s.rttMs() >= 5.0, "expected rtt >= 5ms (clock must start before send), got " + s.rttMs());
    }

    @Test
    void statusModeUsesAllMarkers() {
        var d = LatencyProbe.Config.defaults();
        var cfg = new LatencyProbe.Config("msg_status_info", true, d.mode(), d.intervalMs(), d.rate(),
                d.rampStartRate(), d.rampStepRate(), d.rampStepMs(), d.rampAbortLossPct(),
                d.senderThreads(), 0, d.timeoutMs(), d.bufferCapacity(), 0);
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
                    d.rampAbortLossPct(), 1, 0, d.timeoutMs(), d.bufferCapacity(), 0);
            assertEquals(250.0, probe(fixed).targetRate(), 1e-9);
        }

        @Test
        void rampStepsIncreaseRateAndRecordSeqs() {
            var d = LatencyProbe.Config.defaults();
            var cfg = new LatencyProbe.Config(d.topic(), false, LatencyProbe.Mode.RAMP,
                    d.intervalMs(), d.rate(), 50, 25, 10_000, 50.0, 1, 0, d.timeoutMs(), d.bufferCapacity(), 0);
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
                    d.intervalMs(), d.rate(), 50, 25, 10_000, 50.0, 1, 0, d.timeoutMs(), d.bufferCapacity(), 0);
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
                    d.rampAbortLossPct(), 2, 0, d.timeoutMs(), d.bufferCapacity(), 0);
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

        /** A blocking send must not lower the achieved rate: the scheduler works on absolute
         *  timestamps, so send duration eats into the gap instead of extending the cycle.
         *  The old sleep-after-send pacing yielded ~33/s here (10ms send + 20ms sleep). */
        @Test
        void blockingSenderStillMeetsTargetRate() throws Exception {
            var d = LatencyProbe.Config.defaults();
            var cfg = new LatencyProbe.Config(d.topic(), false, LatencyProbe.Mode.FIXED_RATE,
                    d.intervalMs(), 50, d.rampStartRate(), d.rampStepRate(), d.rampStepMs(),
                    d.rampAbortLossPct(), 1, 0, d.timeoutMs(), d.bufferCapacity(), 0);
            java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
            LatencyProbe p = new LatencyProbe(cfg, new LatencyStats(1000),
                    m -> { count.incrementAndGet();
                           try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } },
                    System::nanoTime, System::currentTimeMillis);
            p.start();
            Thread.sleep(1000);
            p.stop();
            int sent = count.get();
            assertTrue(sent >= 42, "expected ~50 sends/s despite 10ms blocking send, got " + sent);
            assertTrue(sent <= 62, "must not overshoot the target rate, got " + sent);
        }

        /** When a send stalls longer than a full period, the schedule rebases instead of
         *  firing a burst of catch-up sends that would distort latency percentiles. */
        @Test
        void noCatchUpBurstAfterStall() throws Exception {
            var d = LatencyProbe.Config.defaults();
            var cfg = new LatencyProbe.Config(d.topic(), false, LatencyProbe.Mode.FIXED_RATE,
                    d.intervalMs(), 100, d.rampStartRate(), d.rampStepRate(), d.rampStepMs(),
                    d.rampAbortLossPct(), 1, 0, d.timeoutMs(), d.bufferCapacity(), 0);
            List<Long> sendTimes = java.util.Collections.synchronizedList(new ArrayList<>());
            java.util.concurrent.atomic.AtomicBoolean stallOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
            LatencyProbe p = new LatencyProbe(cfg, new LatencyStats(1000),
                    m -> { sendTimes.add(System.nanoTime());
                           if (stallOnce.getAndSet(false)) {
                               try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                           } },
                    System::nanoTime, System::currentTimeMillis);
            p.start();
            Thread.sleep(600);
            p.stop();
            // after the 200ms stall (≈20 missed 10ms slots) no burst: consecutive sends stay >= ~5ms apart
            int bursts = 0;
            synchronized (sendTimes) {
                for (int i = 2; i < sendTimes.size(); i++) {
                    if (sendTimes.get(i) - sendTimes.get(i - 1) < 5_000_000L) bursts++;
                }
            }
            assertTrue(bursts <= 1, "expected rebase instead of catch-up burst, got " + bursts + " sub-5ms gaps");
        }
    }

    @org.junit.jupiter.api.Nested
    class Warmup {

        private LatencyProbe.Config warmupCfg(long warmupMs) {
            var d = LatencyProbe.Config.defaults();
            return new LatencyProbe.Config(d.topic(), false, d.mode(), d.intervalMs(), d.rate(),
                    d.rampStartRate(), d.rampStepRate(), d.rampStepMs(), d.rampAbortLossPct(),
                    d.senderThreads(), 0, d.timeoutMs(), d.bufferCapacity(), warmupMs);
        }

        @Test
        void samplesWithinWarmupWindowAreFlagged() {
            LatencyProbe p = probe(warmupCfg(5000));
            Msg early = p.sendPing();                    // wall = 100_000 = run start
            wall.addAndGet(6000);                        // past the 5s warmup window
            Msg late = p.sendPing();
            nanos.addAndGet(2_000_000);
            p.handleAnswer(answerTo(early, "pong-1", null));
            p.handleAnswer(answerTo(late, "pong-1", null));
            var samples = p.stats().snapshot().samples();
            assertEquals(2, samples.size());
            assertTrue(samples.get(0).warmup(), "ping sent at run start must be warmup");
            assertFalse(samples.get(1).warmup(), "ping sent after the window must not be warmup");
        }

        @Test
        void warmupDisabledByDefault() {
            LatencyProbe p = probe(warmupCfg(0));
            Msg ping = p.sendPing();
            p.handleAnswer(answerTo(ping, "pong-1", null));
            assertFalse(p.stats().snapshot().samples().get(0).warmup());
        }

        @Test
        void statsExcludeWarmupFromLatencyButKeepDeliveryCount() {
            LatencyProbe p = probe(warmupCfg(5000));
            Msg early = p.sendPing();
            nanos.addAndGet(50_000_000);                 // slow warmup answer: 50ms
            p.handleAnswer(answerTo(early, "pong-1", null));
            wall.addAndGet(6000);
            Msg late = p.sendPing();
            nanos.addAndGet(2_000_000);                  // steady state: 2ms
            p.handleAnswer(answerTo(late, "pong-1", null));
            var r = p.stats().snapshot().responders().get(0);
            assertEquals(2, r.count(), "delivery count must include warmup answers");
            assertEquals(2.0, r.avgMs(), 1e-6, "latency avg must exclude the 50ms warmup sample");
            assertEquals(2.0, r.maxMs(), 1e-6, "latency max must exclude the 50ms warmup sample");
            assertEquals(2.0, r.p99(), 1e-6, "percentiles must exclude warmup samples");
        }
    }

    // --- PingOutcomeListener hook (uptime feature) ---

    private record Outcome(long seq, long sentTs, java.util.Set<String> answeredBy) {}

    @Test
    void outcomeListenerFiresOnSweepWithAnsweredSet() {
        LatencyProbe p = probe(LatencyProbe.Config.defaults());
        List<Outcome> outcomes = new ArrayList<>();
        p.setOutcomeListener((seq, sentTs, answeredBy) -> outcomes.add(new Outcome(seq, sentTs, answeredBy)));
        Msg ping1 = p.sendPing();
        p.sendPing();
        nanos.addAndGet(1_000_000);
        p.handleAnswer(answerTo(ping1, "pong-1", null));
        nanos.addAndGet(6_000_000_000L);   // > 5s timeout
        p.sweep();
        assertEquals(2, outcomes.size());
        assertEquals(1, outcomes.get(0).seq());
        assertEquals(100_000L, outcomes.get(0).sentTs());
        assertEquals(java.util.Set.of("pong-1/hostX"), outcomes.get(0).answeredBy());
        assertEquals(java.util.Set.of(), outcomes.get(1).answeredBy());
    }

    @Test
    void outcomeListenerFiresOnEviction() {
        var d = LatencyProbe.Config.defaults();
        var cfg = new LatencyProbe.Config(d.topic(), false, d.mode(), d.intervalMs(), d.rate(),
                d.rampStartRate(), d.rampStepRate(), d.rampStepMs(), d.rampAbortLossPct(),
                d.senderThreads(), 0, d.timeoutMs(), 3, d.warmupMs());
        LatencyProbe p = probe(cfg);
        List<Outcome> outcomes = new ArrayList<>();
        p.setOutcomeListener((seq, sentTs, answeredBy) -> outcomes.add(new Outcome(seq, sentTs, answeredBy)));
        for (int i = 0; i < 5; i++) p.sendPing();
        assertEquals(2, outcomes.size(), "two evicted pings must fire the listener");
        assertEquals(java.util.Set.of(), outcomes.get(0).answeredBy());
    }

    @Test
    void outcomeListenerSendErrorFires() {
        List<Long> errors = new ArrayList<>();
        LatencyProbe p = new LatencyProbe(LatencyProbe.Config.defaults(), new LatencyStats(10),
                m -> { throw new RuntimeException("boom"); }, nanos::get, wall::get);
        p.setOutcomeListener(new PingOutcomeListener() {
            @Override public void onPingComplete(long seq, long sentTs, java.util.Set<String> answeredBy) { }
            @Override public void onSendError(long sentTs) { errors.add(sentTs); }
        });
        p.sendPing();
        assertEquals(List.of(100_000L), errors);
    }

    @Test
    void noListenerBehavesAsBefore() {
        LatencyProbe p = probe(LatencyProbe.Config.defaults());
        p.sendPing();
        nanos.addAndGet(6_000_000_000L);
        assertEquals(1, p.sweep());   // must not NPE without a listener
    }
}

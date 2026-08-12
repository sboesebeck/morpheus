package de.caluga.morpheus.core.latency;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PongResponderTest {

    @Test
    void answersWithRecvTsSeqAndName() {
        PongResponder pong = new PongResponder("pong-7", () -> 42_000L);
        Msg ping = new Msg("morpheus_latency", "ping", "run:5", 5000);
        ping.setMsgId(new MorphiumId());
        ping.setMapValue(Map.of("seq", 5L, "sentTs", 41_990L));
        Msg answer = pong.onMessage(null, ping);
        assertNotNull(answer);
        assertEquals(42_000L, ((Number) answer.getMapValue().get("recvTs")).longValue());
        assertEquals(5L, ((Number) answer.getMapValue().get("seq")).longValue());
        assertEquals("pong-7", answer.getMapValue().get("responder"));
        assertEquals(1, pong.received());
        assertEquals(1, pong.answered());
    }

    @Test
    void ignoresAnswers() {
        PongResponder pong = new PongResponder("p", () -> 1L);
        Msg answer = new Msg("morpheus_latency", "pong", "v", 1000);
        answer.setMsgId(new MorphiumId());
        answer.setInAnswerTo(new MorphiumId());
        assertNull(pong.onMessage(null, answer));
        assertEquals(0, pong.received());
    }

    @Test
    void padsAnswerWhenPayloadConfigured() {
        PongResponder pong = new PongResponder("p", () -> 1L, 2048);
        Msg ping = new Msg("t", "ping", "v", 1000);
        ping.setMsgId(new MorphiumId());
        Msg answer = pong.onMessage(null, ping);
        assertNotNull(answer);
        assertEquals(2048, ((String) answer.getMapValue().get("pad")).length());
    }

    @Test
    void noPadWithoutPayload() {
        PongResponder pong = new PongResponder("p", () -> 1L);
        Msg ping = new Msg("t", "ping", "v", 1000);
        ping.setMsgId(new MorphiumId());
        assertNull(pong.onMessage(null, ping).getMapValue().get("pad"));
    }

    @Test
    void simulatedWorkDelaysAnswerButRecvTsIsArrivalTime() {
        // clock advances on every query: first query (arrival) returns 100, later ones 999 —
        // recvTs must be the arrival time, taken before the simulated work
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(100);
        PongResponder pong = new PongResponder("p", () -> clock.getAndSet(999), 0, 50);
        Msg ping = new Msg("t", "ping", "v", 1000);
        ping.setMsgId(new MorphiumId());
        long start = System.nanoTime();
        Msg answer = pong.onMessage(null, ping);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertNotNull(answer);
        assertTrue(elapsedMs >= 50, "expected >=50ms simulated work, got " + elapsedMs + "ms");
        assertEquals(100L, ((Number) answer.getMapValue().get("recvTs")).longValue(),
                "recvTs must be captured before the simulated work");
    }

    @Test
    void noWorkDelayByDefault() {
        PongResponder pong = new PongResponder("p", () -> 1L);
        Msg ping = new Msg("t", "ping", "v", 1000);
        ping.setMsgId(new MorphiumId());
        long start = System.nanoTime();
        assertNotNull(pong.onMessage(null, ping));
        assertTrue((System.nanoTime() - start) / 1_000_000 < 40, "no artificial delay without --work");
    }

    @Test
    void pingWithoutMapValueStillAnswered() {
        PongResponder pong = new PongResponder("p", () -> 7L);
        Msg ping = new Msg("t", "ping", "v", 1000);
        ping.setMsgId(new MorphiumId());
        Msg answer = pong.onMessage(null, ping);
        assertNotNull(answer);
        assertEquals(7L, ((Number) answer.getMapValue().get("recvTs")).longValue());
        assertNull(answer.getMapValue().get("seq"));
    }
}

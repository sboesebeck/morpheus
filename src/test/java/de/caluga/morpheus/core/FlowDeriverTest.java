package de.caluga.morpheus.core;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FlowDeriverTest {

    private MessageInfo mi(String sender, String topic) {
        Msg msg = new Msg(topic, "m", "v", 1000);
        msg.setMsgId(new MorphiumId());
        msg.setSender(sender);
        msg.setTimestamp(1000);
        MessageInfo info = new MessageInfo(msg, topic);
        info.recipient = "";   // override the constructor default
        return info;
    }

    @Test
    void broadcastFansOutToKnownTopicListenersOnInsert() {
        NodeRegistry reg = new NodeRegistry("self");
        reg.markListener("w1", "h", "order.created", 1);
        reg.markListener("w2", "h", "order.created", 1);
        FlowDeriver d = new FlowDeriver("self", reg);
        List<FlowEvent> ev = d.derive(List.of(mi("hermes", "order.created")), 1000);
        assertEquals(2, ev.size());
        assertTrue(ev.stream().allMatch(e -> e.kind() == FlowEvent.Kind.BROADCAST));
        assertEquals(java.util.Set.of("w1", "w2"),
                ev.stream().map(FlowEvent::to).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void directAndExclusiveEmitNothingOnInsertOrLock() {
        NodeRegistry reg = new NodeRegistry("self");
        FlowDeriver d = new FlowDeriver("self", reg);
        MessageInfo direct = mi("hermes", "t");
        direct.recipient = "w1";
        assertEquals(0, d.derive(List.of(direct), 1000).size(), "directed message: no shot on insert");
        MessageInfo excl = mi("hermes", "jobs");
        excl.isExclusive = true;
        assertEquals(0, d.derive(List.of(excl), 1000).size(), "exclusive: no shot on insert");
        excl.lockedBy = "w3";
        assertEquals(0, d.derive(List.of(excl), 1100).size(), "exclusive: no shot on lock");
    }

    @Test
    void answeredRequestEmitsRoundTripOrientedSenderToAnswerer() {
        NodeRegistry reg = new NodeRegistry("self");
        FlowDeriver d = new FlowDeriver("self", reg);
        MessageInfo m = mi("hermes", "order.created");
        m.isExclusive = true;
        d.derive(List.of(m), 1000);                 // insert: nothing
        m.answeredBy = "worker1";
        m.answeredByHost = "wh1";
        m.rtt = 42L;
        List<FlowEvent> ev = d.derive(List.of(m), 1100);
        assertEquals(1, ev.size());
        FlowEvent a = ev.get(0);
        assertEquals(FlowEvent.Kind.ANSWER, a.kind());
        assertEquals("hermes", a.from(), "round-trip is oriented as the request (sender → answerer)");
        assertEquals("worker1", a.to());
        assertEquals(42L, a.rttMs());
        assertTrue(reg.listenersOf("order.created").contains("worker1"));
        assertEquals(0, d.derive(List.of(m), 1200).size(), "answer edge emitted once");
    }

    @Test
    void unansweredDirectTimesOutRedTowardRecipient() {
        NodeRegistry reg = new NodeRegistry("self");
        FlowDeriver d = new FlowDeriver("self", reg);
        MessageInfo m = mi("hermes", "t");
        m.recipient = "w1";
        assertEquals(0, d.derive(List.of(m), 1000).size(), "fresh: not yet timed out");
        List<FlowEvent> ev = d.derive(List.of(m), 1000 + FlowDeriver.TIMEOUT_MS + 1);
        assertEquals(1, ev.size());
        assertEquals(FlowEvent.Kind.TIMEOUT, ev.get(0).kind());
        assertEquals("hermes", ev.get(0).from());
        assertEquals("w1", ev.get(0).to());
        assertEquals(0, d.derive(List.of(m), 1000 + 2 * FlowDeriver.TIMEOUT_MS).size(), "timeout emitted once");
    }

    @Test
    void unansweredExclusiveTimesOutRedFannedToListeners() {
        NodeRegistry reg = new NodeRegistry("self");
        reg.markListener("w1", "h", "jobs", 1);
        reg.markListener("w2", "h", "jobs", 1);
        FlowDeriver d = new FlowDeriver("self", reg);
        MessageInfo m = mi("hermes", "jobs");
        m.isExclusive = true;
        List<FlowEvent> ev = d.derive(List.of(m), 1000 + FlowDeriver.TIMEOUT_MS + 1);
        assertEquals(2, ev.size());
        assertTrue(ev.stream().allMatch(e -> e.kind() == FlowEvent.Kind.TIMEOUT));
    }

    @Test
    void answeredMessageNeverTimesOut() {
        NodeRegistry reg = new NodeRegistry("self");
        FlowDeriver d = new FlowDeriver("self", reg);
        MessageInfo m = mi("hermes", "t");
        m.recipient = "w1";
        m.answeredBy = "w1";
        m.rtt = 5L;
        List<FlowEvent> ev = d.derive(List.of(m), 1000 + FlowDeriver.TIMEOUT_MS + 1);
        assertEquals(1, ev.size());
        assertEquals(FlowEvent.Kind.ANSWER, ev.get(0).kind(), "answered → ANSWER, never TIMEOUT");
    }

    @Test
    void broadcastDoesNotTimeOut() {
        NodeRegistry reg = new NodeRegistry("self");
        reg.markListener("w1", "h", "news", 1);
        FlowDeriver d = new FlowDeriver("self", reg);
        MessageInfo m = mi("hermes", "news");      // broadcast: no recipient, not exclusive
        d.derive(List.of(m), 1000);                // fan-out on insert
        List<FlowEvent> ev = d.derive(List.of(m), 1000 + FlowDeriver.TIMEOUT_MS + 1);
        assertEquals(0, ev.size(), "broadcasts are not flagged red when unanswered");
    }

    @Test
    void answerMessageItselfIsNotCountedAsOutgoing() {
        NodeRegistry reg = new NodeRegistry("self");
        reg.markListener("x", "h", "order.created", 1);
        FlowDeriver d = new FlowDeriver("self", reg);
        MessageInfo answer = mi("worker1", "order.created");
        answer.isAnswer = true;
        assertEquals(0, d.derive(List.of(answer), 1000).size());
    }

    @Test
    void selfTrafficIsDropped() {
        NodeRegistry reg = new NodeRegistry("self");
        reg.markListener("w1", "h", "t", 1);
        FlowDeriver d = new FlowDeriver("self", reg);
        MessageInfo fromSelf = mi("self", "t");
        assertEquals(0, d.derive(List.of(fromSelf), 1000).size());
        assertEquals(1, reg.nodeCount(), "w1 pre-seeded; self never registered");
    }
}

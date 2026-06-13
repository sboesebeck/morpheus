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
        MessageInfo info = new MessageInfo(msg, topic);
        info.recipient = "";   // override the constructor default
        return info;
    }

    @Test
    void directMessageEmitsOneEdgeToRecipient() {
        NodeRegistry reg = new NodeRegistry("self");
        FlowDeriver d = new FlowDeriver("self", reg);
        MessageInfo m = mi("hermes", "order.created");
        m.recipient = "worker1";
        List<FlowEvent> ev = d.derive(List.of(m), 1000);
        assertEquals(1, ev.size());
        assertEquals(FlowEvent.Kind.DIRECT, ev.get(0).kind());
        assertEquals("hermes", ev.get(0).from());
        assertEquals("worker1", ev.get(0).to());
        assertTrue(reg.listenersOf("order.created").contains("worker1"));
    }

    @Test
    void broadcastFansOutToKnownTopicListeners() {
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
    void exclusiveEmitsOnlyWhenLockedByAppears() {
        NodeRegistry reg = new NodeRegistry("self");
        FlowDeriver d = new FlowDeriver("self", reg);
        MessageInfo m = mi("hermes", "jobs");
        m.isExclusive = true;
        assertEquals(0, d.derive(List.of(m), 1000).size(), "no recipient yet -> no edge");
        m.lockedBy = "worker3";
        List<FlowEvent> ev = d.derive(List.of(m), 1100);
        assertEquals(1, ev.size());
        assertEquals(FlowEvent.Kind.EXCLUSIVE, ev.get(0).kind());
        assertEquals("worker3", ev.get(0).to());
        assertEquals(0, d.derive(List.of(m), 1200).size(), "exclusive edge emitted once");
    }

    @Test
    void answerEmitsReplyEdgeWithRttOnce() {
        NodeRegistry reg = new NodeRegistry("self");
        FlowDeriver d = new FlowDeriver("self", reg);
        MessageInfo m = mi("hermes", "order.created");
        d.derive(List.of(m), 1000);          // request seen (no listeners -> no edge)
        m.answeredBy = "worker1";
        m.answeredByHost = "wh1";
        m.rtt = 42L;
        List<FlowEvent> ev = d.derive(List.of(m), 1100);
        assertEquals(1, ev.size());
        FlowEvent a = ev.get(0);
        assertEquals(FlowEvent.Kind.ANSWER, a.kind());
        assertEquals("worker1", a.from());
        assertEquals("hermes", a.to());
        assertEquals(42L, a.rttMs());
        assertTrue(reg.listenersOf("order.created").contains("worker1"));
        assertEquals(0, d.derive(List.of(m), 1200).size(), "answer edge emitted once");
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
        assertEquals(1, reg.nodeCount()); // w1 was pre-registered; self-traffic adds nothing
    }
}

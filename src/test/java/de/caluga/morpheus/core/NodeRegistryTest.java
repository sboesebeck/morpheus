package de.caluga.morpheus.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class NodeRegistryTest {

    private NodeStatus node(String sender, String host, Map<String, Object> raw) {
        return new NodeStatus(sender, host, 5, null, null, null, null, null, null, null, null, raw);
    }

    @Test
    void seedFromStatusRegistersNodesAndTopicListeners() {
        Map<String, Object> listeners = new HashMap<>();
        listeners.put("order.created", List.of("L1"));
        listeners.put("invoice.req", List.of("L2"));
        Map<String, Object> raw = new HashMap<>();
        raw.put("message_listeners_by_name", listeners);

        NodeRegistry r = new NodeRegistry("self");
        r.seedFromStatus(List.of(node("worker1", "h1", raw)), 1000);

        assertEquals(1, r.nodeCount());
        assertEquals(2, r.topicCount());
        assertTrue(r.listenersOf("order.created").contains("worker1"));
        assertTrue(r.listenersOf("invoice.req").contains("worker1"));
    }

    @Test
    void selfIdNeverRegistered() {
        NodeRegistry r = new NodeRegistry("self");
        r.observeSend("self", "h", 1000);
        r.markListener("self", "h", "t", 1000);
        assertEquals(0, r.nodeCount());
        assertTrue(r.listenersOf("t").isEmpty());
    }

    @Test
    void passiveGrowthKeepsStableFirstSeenOrder() {
        NodeRegistry r = new NodeRegistry("self");
        r.observeSend("a", "h", 1000);
        r.observeSend("b", "h", 1000);
        r.observeRecv("c", "h", 1000);
        r.markListener("c", "h", "t", 1000);
        assertEquals(List.of("a", "b", "c"), r.nodes().stream().map(n -> n.id).toList());
        assertTrue(r.listenersOf("t").contains("c"));
    }

    @Test
    void idleAfterThreshold() {
        NodeRegistry r = new NodeRegistry("self");
        r.observeSend("a", "h", 1000);
        assertFalse(r.isIdle("a", 1000 + 5000, 15000));
        assertTrue(r.isIdle("a", 1000 + 20000, 15000));
    }

    @Test
    void listenersOfReturnsACopy() {
        NodeRegistry r = new NodeRegistry("self");
        r.markListener("a", "h", "t", 1000);
        r.listenersOf("t").clear();           // must not affect the registry
        assertTrue(r.listenersOf("t").contains("a"));
    }
}

package de.caluga.morpheus.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class GraphFilterTest {

    private FlowEvent flow(String from, String to, String topic) {
        return new FlowEvent(from, to, topic, FlowEvent.Kind.BROADCAST, null);
    }

    @Test
    void emptyFilterAcceptsEverythingAndHighlightsAll() {
        GraphFilter f = new GraphFilter();
        assertTrue(f.isEmpty());
        assertTrue(f.acceptsFlow(flow("a", "b", "orders")));
        assertTrue(f.isHighlighted("a", new NodeRegistry("self")));
    }

    @Test
    void topicFilterIsOrWithinAxis() {
        GraphFilter f = new GraphFilter();
        f.topics.add("orders");
        f.topics.add("shipping");
        assertFalse(f.isEmpty());
        assertTrue(f.acceptsFlow(flow("a", "b", "orders")));
        assertTrue(f.acceptsFlow(flow("a", "b", "shipping")));
        assertFalse(f.acceptsFlow(flow("a", "b", "auth")));
    }

    @Test
    void nodeFilterMatchesFromOrTo() {
        GraphFilter f = new GraphFilter();
        f.nodes.add("hermes");
        assertTrue(f.acceptsFlow(flow("hermes", "b", "orders")));   // as source
        assertTrue(f.acceptsFlow(flow("a", "hermes", "orders")));   // as target
        assertFalse(f.acceptsFlow(flow("a", "b", "orders")));
    }

    @Test
    void axesCombineWithAnd() {
        GraphFilter f = new GraphFilter();
        f.topics.add("orders");
        f.nodes.add("hermes");
        assertTrue(f.acceptsFlow(flow("hermes", "b", "orders")));   // both axes satisfied
        assertFalse(f.acceptsFlow(flow("hermes", "b", "auth")));    // wrong topic
        assertFalse(f.acceptsFlow(flow("a", "b", "orders")));       // hermes not involved
    }

    @Test
    void highlightFollowsTopicListenersAndNodeSet() {
        NodeRegistry reg = new NodeRegistry("self");
        reg.markListener("worker1", "h1", "orders", 1000);   // worker1 listens on orders
        GraphFilter f = new GraphFilter();
        f.topics.add("orders");
        assertTrue(f.isHighlighted("worker1", reg));         // listener of an active topic -> bright
        assertFalse(f.isHighlighted("other", reg));          // not involved -> dimmed

        GraphFilter g = new GraphFilter();
        g.nodes.add("hermes");
        assertTrue(g.isHighlighted("hermes", reg));          // in the node set -> bright
        assertFalse(g.isHighlighted("worker1", reg));
    }

    @Test
    void clearResetsBothAxes() {
        GraphFilter f = new GraphFilter();
        f.topics.add("orders");
        f.nodes.add("hermes");
        f.clear();
        assertTrue(f.isEmpty());
    }
}

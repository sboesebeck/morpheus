package de.caluga.morpheus.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Known messaging nodes (by sender id) plus which nodes listen on which topic.
 *  Seeded from a status ping, grown passively from the message stream. The tool's
 *  own sender id is never registered. Single-threaded (the TUI render thread). */
public class NodeRegistry {

    /** One observed node. Mutable counters; ordered by first-seen. */
    public static final class Node {
        public final String id;
        public String host;
        public long lastSeenMs;
        public long sendCount;
        public long recvCount;

        Node(final String id, final String host, final long now) {
            this.id = id;
            this.host = host;
            this.lastSeenMs = now;
        }
    }

    private final String selfId;
    private final LinkedHashMap<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, Set<String>> topicListeners = new HashMap<>();

    public NodeRegistry(final String selfId) {
        this.selfId = selfId == null ? "" : selfId;
    }

    private Node touch(final String id, final String host, final long now) {
        if (id == null || id.equals(selfId)) {
            return null;
        }
        final Node n = nodes.computeIfAbsent(id, k -> new Node(id, host, now));
        if (host != null && !host.isEmpty()) {
            n.host = host;
        }
        n.lastSeenMs = now;
        return n;
    }

    private void addListener(final String topic, final String id) {
        if (topic == null || id == null || id.equals(selfId)) {
            return;
        }
        topicListeners.computeIfAbsent(topic, k -> new LinkedHashSet<>()).add(id);
    }

    /** Seed from one status-ping roster: register each answering node and invert its
     *  message_listeners_by_name (keys = subscribed topics) into topic -> listeners. */
    public void seedFromStatus(final List<NodeStatus> roster, final long now) {
        if (roster == null) {
            return;
        }
        for (final NodeStatus s : roster) {
            final Node n = touch(s.sender(), s.host(), now);
            if (n == null) {
                continue;
            }
            final Object lm = s.raw() == null ? null : s.raw().get("message_listeners_by_name");
            if (lm instanceof Map<?, ?> m) {
                for (final Object topic : m.keySet()) {
                    addListener(String.valueOf(topic), s.sender());
                }
            }
        }
    }

    /** A node originated a message (edge source). */
    public void observeSend(final String id, final String host, final long now) {
        final Node n = touch(id, host, now);
        if (n != null) {
            n.sendCount++;
        }
    }

    /** A node received a message (edge destination). */
    public void observeRecv(final String id, final String host, final long now) {
        final Node n = touch(id, host, now);
        if (n != null) {
            n.recvCount++;
        }
    }

    /** Record that a node subscribes to a topic (no counter change). */
    public void markListener(final String id, final String host, final String topic, final long now) {
        if (touch(id, host, now) != null) {
            addListener(topic, id);
        }
    }

    /** Listener ids for a topic (a copy; empty if unknown). */
    public Set<String> listenersOf(final String topic) {
        return new LinkedHashSet<>(topicListeners.getOrDefault(topic, Set.of()));
    }

    /** Nodes in stable first-seen order (live Node references). */
    public List<Node> nodes() {
        return new ArrayList<>(nodes.values());
    }

    public boolean isIdle(final String id, final long now, final long idleMs) {
        final Node n = nodes.get(id);
        return n == null || now - n.lastSeenMs > idleMs;
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int topicCount() {
        return topicListeners.size();
    }

    /** Known topic names in insertion-ish order (a copy). */
    public List<String> topicNames() {
        return new ArrayList<>(topicListeners.keySet());
    }
}

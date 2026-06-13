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

        Node(String id, String host, long now) {
            this.id = id;
            this.host = host;
            this.lastSeenMs = now;
        }
    }

    private final String selfId;
    private final LinkedHashMap<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, Set<String>> topicListeners = new HashMap<>();

    public NodeRegistry(String selfId) {
        this.selfId = selfId == null ? "" : selfId;
    }

    private Node touch(String id, String host, long now) {
        if (id == null || id.equals(selfId)) return null;
        Node n = nodes.computeIfAbsent(id, k -> new Node(id, host, now));
        if (host != null && !host.isEmpty()) n.host = host;
        n.lastSeenMs = now;
        return n;
    }

    private void addListener(String topic, String id) {
        if (topic == null || id == null || id.equals(selfId)) return;
        topicListeners.computeIfAbsent(topic, k -> new LinkedHashSet<>()).add(id);
    }

    /** Seed from one status-ping roster: register each answering node and invert its
     *  message_listeners_by_name (keys = subscribed topics) into topic -> listeners. */
    public void seedFromStatus(List<NodeStatus> roster, long now) {
        if (roster == null) return;
        for (NodeStatus s : roster) {
            Node n = touch(s.sender(), s.host(), now);
            if (n == null) continue;
            Object lm = s.raw() == null ? null : s.raw().get("message_listeners_by_name");
            if (lm instanceof Map<?, ?> m) {
                for (Object topic : m.keySet()) {
                    addListener(String.valueOf(topic), s.sender());
                }
            }
        }
    }

    /** A node originated a message (edge source). */
    public void observeSend(String id, String host, long now) {
        Node n = touch(id, host, now);
        if (n != null) n.sendCount++;
    }

    /** A node received a message (edge destination). */
    public void observeRecv(String id, String host, long now) {
        Node n = touch(id, host, now);
        if (n != null) n.recvCount++;
    }

    /** Record that a node subscribes to a topic (no counter change). */
    public void markListener(String id, String host, String topic, long now) {
        if (touch(id, host, now) != null) addListener(topic, id);
    }

    /** Listener ids for a topic (a copy; empty if unknown). */
    public Set<String> listenersOf(String topic) {
        return new LinkedHashSet<>(topicListeners.getOrDefault(topic, Set.of()));
    }

    /** Nodes in stable first-seen order (live Node references). */
    public List<Node> nodes() {
        return new ArrayList<>(nodes.values());
    }

    public boolean isIdle(String id, long now, long idleMs) {
        Node n = nodes.get(id);
        return n == null || now - n.lastSeenMs > idleMs;
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int topicCount() {
        return topicListeners.size();
    }
}

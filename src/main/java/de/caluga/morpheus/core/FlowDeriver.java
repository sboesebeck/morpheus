package de.caluga.morpheus.core;

import de.caluga.morphium.driver.MorphiumId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Derives discrete flow events from successive MessageInfo snapshots and updates
 *  the NodeRegistry as recipients are discovered. Single-threaded (TUI render thread). */
public class FlowDeriver {

    /** What we've already emitted for a message we've seen before. */
    private static final class Seen {
        boolean answerEmitted;
        boolean exclusiveEmitted;
    }

    private final String selfId;
    private final NodeRegistry registry;
    private final Map<MorphiumId, Seen> seen = new HashMap<>();

    public FlowDeriver(String selfId, NodeRegistry registry) {
        this.selfId = selfId == null ? "" : selfId;
        this.registry = registry;
    }

    public List<FlowEvent> derive(List<MessageInfo> snapshot, long now) {
        List<FlowEvent> out = new ArrayList<>();
        Set<MorphiumId> present = new HashSet<>();
        for (MessageInfo m : snapshot) {
            if (m.id == null) continue;
            present.add(m.id);
            Seen s = seen.get(m.id);
            boolean isNew = s == null;
            if (isNew) {
                s = new Seen();
                seen.put(m.id, s);
                if (!m.isAnswer) onNewOutgoing(m, now, out);
            }
            if (!s.answerEmitted && m.answeredBy != null && !m.answeredBy.isEmpty()) {
                s.answerEmitted = true;
                // reply: the answerer (from) is the topic listener; it lands back on the requester
                emit(out, now, m.answeredBy, m.answeredByHost, m.sender, m.senderHost,
                        m.answeredBy, m.answeredByHost, m.topic, FlowEvent.Kind.ANSWER, m.rtt);
            }
            if (!s.exclusiveEmitted && m.isExclusive && m.lockedBy != null && !m.lockedBy.isEmpty()) {
                s.exclusiveEmitted = true;
                emit(out, now, m.sender, m.senderHost, m.lockedBy, null,
                        m.lockedBy, null, m.topic, FlowEvent.Kind.EXCLUSIVE, null);
            }
        }
        seen.keySet().retainAll(present);   // forget evicted messages
        return out;
    }

    private void onNewOutgoing(MessageInfo m, long now, List<FlowEvent> out) {
        if (m.recipient != null && !m.recipient.isEmpty()) {
            emit(out, now, m.sender, m.senderHost, m.recipient, null,
                    m.recipient, null, m.topic, FlowEvent.Kind.DIRECT, null);
        } else if (!m.isExclusive) {
            for (String l : registry.listenersOf(m.topic)) {
                if (!l.equals(m.sender)) {
                    emit(out, now, m.sender, m.senderHost, l, null,
                            l, null, m.topic, FlowEvent.Kind.BROADCAST, null);
                }
            }
        }
        // exclusive without a recipient: resolved when lockedBy appears (in derive())
    }

    /** Records one edge: send on `from`, recv on `to`, subscription on `listenerId`; drops self/loop. */
    private void emit(List<FlowEvent> out, long now,
                      String from, String fromHost, String to, String toHost,
                      String listenerId, String listenerHost, String topic,
                      FlowEvent.Kind kind, Long rtt) {
        if (from == null || to == null) return;
        if (from.equals(selfId) || to.equals(selfId)) return;
        if (kind != FlowEvent.Kind.BROADCAST && from.equals(to)) return;
        registry.observeSend(from, fromHost, now);
        registry.observeRecv(to, toHost, now);
        if (listenerId != null) registry.markListener(listenerId, listenerHost, topic, now);
        out.add(new FlowEvent(from, to, topic, kind, rtt));
    }
}

package de.caluga.morpheus.core;

import de.caluga.morphium.driver.MorphiumId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Derives discrete flow events from successive MessageInfo snapshots and updates
 *  the NodeRegistry as recipients are discovered. Single-threaded (TUI render thread).
 *
 *  Emission model:
 *   - Broadcast (non-exclusive, no recipient): fan-out shots on first sight.
 *   - Request/reply (directed or exclusive): driven by the outcome, not the insert —
 *       answered   → one ANSWER event (sender → answerer; the screen animates the request
 *                    leg, then the reply leg back),
 *       unanswered after TIMEOUT_MS → a TIMEOUT (red) event toward the recipient / topic listeners.
 *   - The tool's own traffic is dropped. */
public class FlowDeriver {

    /** Unanswered request/reply messages older than this are flagged as timed-out (red). */
    static final long TIMEOUT_MS = 5000;

    private static final class Seen {
        boolean answerEmitted;
        boolean timeoutEmitted;
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
            if (m.sender != null && m.sender.equals(selfId)) continue;   // never track our own traffic
            present.add(m.id);
            Seen s = seen.get(m.id);
            if (s == null) {
                s = new Seen();
                seen.put(m.id, s);
                if (!m.isAnswer) onNewBroadcast(m, now, out);
            }
            if (!s.answerEmitted && m.answeredBy != null && !m.answeredBy.isEmpty()) {
                s.answerEmitted = true;
                // One round-trip event oriented as the request (sender → answerer); the screen
                // animates the request leg, then the reply leg back. The answerer is a topic listener.
                emit(out, now, m.sender, m.senderHost, m.answeredBy, m.answeredByHost,
                        m.answeredBy, m.answeredByHost, m.topic, FlowEvent.Kind.ANSWER, m.rtt);
            } else if (!s.answerEmitted && !s.timeoutEmitted && !m.isAnswer
                    && isRequestType(m) && now - m.timestamp > TIMEOUT_MS) {
                s.timeoutEmitted = true;
                onTimeout(m, now, out);
            }
        }
        seen.keySet().retainAll(present);   // forget evicted messages
        return out;
    }

    /** Request/reply messages expect an answer: directed (recipient set) or exclusive (queue). */
    private boolean isRequestType(MessageInfo m) {
        return (m.recipient != null && !m.recipient.isEmpty()) || m.isExclusive;
    }

    /** Only broadcasts (non-exclusive, no recipient) produce a shot on insert. */
    private void onNewBroadcast(MessageInfo m, long now, List<FlowEvent> out) {
        if (m.isExclusive || (m.recipient != null && !m.recipient.isEmpty())) return;
        for (String l : registry.listenersOf(m.topic)) {
            if (!l.equals(m.sender)) {
                emit(out, now, m.sender, m.senderHost, l, null,
                        l, null, m.topic, FlowEvent.Kind.BROADCAST, null);
            }
        }
    }

    /** An unanswered request/reply: red toward its recipient, or fanned to the topic's listeners. */
    private void onTimeout(MessageInfo m, long now, List<FlowEvent> out) {
        if (m.recipient != null && !m.recipient.isEmpty()) {
            emit(out, now, m.sender, m.senderHost, m.recipient, null,
                    m.recipient, null, m.topic, FlowEvent.Kind.TIMEOUT, null);
        } else {
            for (String l : registry.listenersOf(m.topic)) {
                if (!l.equals(m.sender)) {
                    emit(out, now, m.sender, m.senderHost, l, null,
                            l, null, m.topic, FlowEvent.Kind.TIMEOUT, null);
                }
            }
        }
    }

    /** Records one edge: send on `from`, recv on `to`, subscription on `listenerId`; drops self/loop. */
    private void emit(List<FlowEvent> out, long now,
                      String from, String fromHost, String to, String toHost,
                      String listenerId, String listenerHost, String topic,
                      FlowEvent.Kind kind, Long rtt) {
        if (from == null || to == null) return;
        if (from.equals(selfId) || to.equals(selfId)) return;
        if (from.equals(to)) return;
        registry.observeSend(from, fromHost, now);
        registry.observeRecv(to, toHost, now);
        if (listenerId != null) registry.markListener(listenerId, listenerHost, topic, now);
        out.add(new FlowEvent(from, to, topic, kind, rtt));
    }
}

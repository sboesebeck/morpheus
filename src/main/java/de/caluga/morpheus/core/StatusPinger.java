package de.caluga.morpheus.core;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.MorphiumMessaging;
import de.caluga.morphium.messaging.Msg;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Sends one status PING and parses the answers into a node roster:
 * sender, host, round-trip time, and the topics each node has listeners for.
 */
public class StatusPinger {

    /** Key the StatusInfoListener uses for the topic->listeners map in its answer. */
    private static final String LISTENERS_KEY = "message_listeners_by_name";

    public record NodeEntry(String sender, String host, long rttMs, List<String> topics) {}

    private final MorphiumMessaging messaging;

    public StatusPinger(MorphiumMessaging messaging) {
        this.messaging = messaging;
    }

    /** Sends a MESSAGING_ONLY ping; returns one NodeEntry per answer (sorted by sender). Never throws. */
    public List<NodeEntry> ping(long timeoutMs) {
        try {
            Msg msg = new Msg(messaging.getStatusInfoListenerName(), "ALL", "MESSAGING_ONLY", timeoutMs);
            msg.setMsgId(new MorphiumId());
            long sendTime = System.currentTimeMillis();
            var answers = messaging.sendAndAwaitAnswers(msg, 10000, timeoutMs);
            List<NodeEntry> out = new ArrayList<>();
            for (Msg a : answers) {
                out.add(parse(a, sendTime));
            }
            out.sort(Comparator.comparing(NodeEntry::sender, Comparator.nullsLast(Comparator.naturalOrder())));
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Parses one answer into a NodeEntry. Topics come from the listeners map's keys. */
    static NodeEntry parse(Msg answer, long sendTime) {
        long rtt = Math.max(0, answer.getTimestamp() - sendTime);
        List<String> topics = new ArrayList<>();
        Map<String, Object> map = answer.getMapValue();
        if (map != null) {
            Object listeners = map.get(LISTENERS_KEY);
            if (listeners instanceof Map<?, ?> lm) {
                for (Object k : lm.keySet()) {
                    topics.add(String.valueOf(k));
                }
            }
        }
        topics.sort(Comparator.naturalOrder());
        return new NodeEntry(answer.getSender(), answer.getSenderHost(), rtt, topics);
    }
}

package de.caluga.morpheus.core;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains the bounded buffer of observed messages, correlates answers to
 * requests (RTT), marks timeouts, and feeds MessageStats.
 * Receives deserialized Msg objects from a feed; does no I/O itself.
 */
public class MessageTracker {
    private volatile int maxMessages;
    private final MessageStats stats = new MessageStats();

    private final Map<MorphiumId, MessageInfo> buffer =
        new LinkedHashMap<>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<MorphiumId, MessageInfo> eldest) {
                return size() > maxMessages;
            }
        };

    private final ConcurrentHashMap<MorphiumId, Long> requestTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<MorphiumId, String> requestTopics = new ConcurrentHashMap<>();

    public MessageTracker(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
        synchronized (buffer) {
            var it = buffer.entrySet().iterator();
            while (buffer.size() > maxMessages && it.hasNext()) {
                it.next();
                it.remove();
            }
        }
    }

    public MessageStats getStats() { return stats; }

    /** A new message document appeared. topic is extracted by the feed (V6 'topic' / V5 'name'). */
    public void onInsert(Msg msg, String topic) {
        stats.recordMessage(msg.getSender(), msg.getSenderHost());

        if (msg.isAnswer() && msg.getInAnswerTo() != null) {
            stats.recordAnswer();
            correlateAnswer(msg);
            return; // answers are not buffered - we track request status
        }
        if (msg.isAnswer()) {
            return;
        }

        MessageInfo info = new MessageInfo(msg, topic);
        requestTimestamps.put(msg.getMsgId(), msg.getTimestamp());
        if (topic != null && !topic.isEmpty()) {
            requestTopics.put(msg.getMsgId(), topic);
        }
        synchronized (buffer) {
            buffer.put(msg.getMsgId(), info);
        }
    }

    private void correlateAnswer(Msg answer) {
        MorphiumId originalId = answer.getInAnswerTo();
        Long originalTime = requestTimestamps.remove(originalId);
        String originalTopic = requestTopics.remove(originalId);
        if (originalTime == null) {
            return;
        }
        long rtt = answer.getTimestamp() - originalTime;
        if (rtt < 0) {
            return;
        }
        synchronized (buffer) {
            MessageInfo original = buffer.get(originalId);
            if (original != null) {
                original.rtt = rtt;
                original.isTimedOut = false;
                original.answeredBy = answer.getSender();
                original.answeredByHost = answer.getSenderHost();
                original.answerIsV5 = MessageInfo.detectV5(answer);
            }
        }
        stats.recordRtt(originalTopic, rtt);
    }

    /** An existing message document changed (e.g. processed_by grew). */
    public void onUpdate(Msg msg) {
        synchronized (buffer) {
            MessageInfo existing = buffer.get(msg.getMsgId());
            if (existing != null) {
                existing.update(msg);
                stats.recordUpdate();
            }
        }
    }

    /** A message document was deleted. */
    public void onDelete(MorphiumId id) {
        synchronized (buffer) {
            MessageInfo existing = buffer.get(id);
            if (existing != null) {
                existing.isDeleted = true;
                existing.updateCount++;
            }
        }
    }

    /** Flags unanswered requests older than threshold. Returns number of NEW timeouts. */
    public int markTimeouts(long now, long thresholdMs) {
        int newTimeouts = 0;
        synchronized (buffer) {
            for (MessageInfo info : buffer.values()) {
                if (!info.isAnswer && info.rtt == null && !info.isTimedOut
                        && now - info.timestamp > thresholdMs) {
                    info.isTimedOut = true;
                    newTimeouts++;
                }
            }
        }
        if (newTimeouts > 0) {
            stats.recordTimeouts(newTimeouts);
        }
        return newTimeouts;
    }

    /** Snapshot copy, newest first (display order). */
    public List<MessageInfo> getMessagesNewestFirst() {
        synchronized (buffer) {
            List<MessageInfo> list = new ArrayList<>(buffer.values());
            Collections.reverse(list);
            return list;
        }
    }

    public int getBufferSize() {
        synchronized (buffer) {
            return buffer.size();
        }
    }
}

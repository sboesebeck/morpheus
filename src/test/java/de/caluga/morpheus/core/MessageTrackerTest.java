package de.caluga.morpheus.core;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MessageTrackerTest {

    private Msg msg(String topic, String sender, String host) {
        Msg m = new Msg(topic, "m", "v", 30000);
        m.setMsgId(new MorphiumId());
        m.setSender(sender);
        m.setSenderHost(host);
        m.setTimestamp(System.currentTimeMillis());
        return m;
    }

    @Test
    void insertAddsRequestToBufferAndStats() {
        MessageTracker tracker = new MessageTracker(10);
        Msg m = msg("t1", "s1", "h1");
        tracker.onInsert(m, "t1");

        List<MessageInfo> msgs = tracker.getMessagesNewestFirst();
        assertEquals(1, msgs.size());
        assertEquals("s1", msgs.get(0).sender);
        assertEquals(1, tracker.getStats().getTotalMessages());
        assertEquals(1, tracker.getStats().getSenderCount("s1"));
    }

    @Test
    void answerCorrelatesRttToOriginalAndIsNotBuffered() {
        MessageTracker tracker = new MessageTracker(10);
        Msg original = msg("t1", "s1", "h1");
        original.setTimestamp(1000);
        tracker.onInsert(original, "t1");

        Msg answer = msg("t1", "s2", "h2");
        answer.setInAnswerTo(original.getMsgId());
        answer.setTimestamp(1250);
        tracker.onInsert(answer, "t1");

        List<MessageInfo> msgs = tracker.getMessagesNewestFirst();
        assertEquals(1, msgs.size(), "answers must not be buffered");
        MessageInfo info = msgs.get(0);
        assertEquals(250, info.rtt);
        assertEquals("s2", info.answeredBy);
        assertEquals("h2", info.answeredByHost);
        assertEquals(1, tracker.getStats().getTotalAnswers());
        assertEquals(250, tracker.getStats().getAvgRtt("t1"));
    }

    @Test
    void updateIncreasesProcessedCount() {
        MessageTracker tracker = new MessageTracker(10);
        Msg m = msg("t1", "s1", "h1");
        tracker.onInsert(m, "t1");

        m.setProcessedBy(List.of("node-a"));
        tracker.onUpdate(m);

        MessageInfo info = tracker.getMessagesNewestFirst().get(0);
        assertEquals(1, info.processedByCount);
        assertEquals(1, tracker.getStats().getTotalUpdates());
    }

    @Test
    void deleteMarksMessageDeleted() {
        MessageTracker tracker = new MessageTracker(10);
        Msg m = msg("t1", "s1", "h1");
        tracker.onInsert(m, "t1");

        tracker.onDelete(m.getMsgId());

        assertTrue(tracker.getMessagesNewestFirst().get(0).isDeleted);
    }

    @Test
    void bufferEvictsOldestBeyondMax() {
        MessageTracker tracker = new MessageTracker(2);
        tracker.onInsert(msg("t1", "s1", "h1"), "t1");
        tracker.onInsert(msg("t2", "s2", "h2"), "t2");
        tracker.onInsert(msg("t3", "s3", "h3"), "t3");

        List<MessageInfo> msgs = tracker.getMessagesNewestFirst();
        assertEquals(2, msgs.size());
        assertEquals("t3", msgs.get(0).topic);
        assertEquals("t2", msgs.get(1).topic);
    }

    @Test
    void markTimeoutsFlagsOldUnansweredRequests() {
        MessageTracker tracker = new MessageTracker(10);
        Msg old = msg("t1", "s1", "h1");
        old.setTimestamp(System.currentTimeMillis() - 10_000);
        tracker.onInsert(old, "t1");
        Msg fresh = msg("t2", "s2", "h2");
        tracker.onInsert(fresh, "t2");

        int newTimeouts = tracker.markTimeouts(System.currentTimeMillis(), 2000);

        assertEquals(1, newTimeouts);
        assertEquals(1, tracker.getStats().getTotalTimeouts());
        List<MessageInfo> msgs = tracker.getMessagesNewestFirst();
        assertTrue(msgs.get(1).isTimedOut);
        assertFalse(msgs.get(0).isTimedOut);
    }

    @Test
    void maxMessagesIsAdjustable() {
        MessageTracker tracker = new MessageTracker(5);
        tracker.setMaxMessages(1);
        tracker.onInsert(msg("t1", "s1", "h1"), "t1");
        tracker.onInsert(msg("t2", "s2", "h2"), "t2");
        assertEquals(1, tracker.getMessagesNewestFirst().size());
    }

    @Test
    void negativeRttIsIgnored() {
        MessageTracker tracker = new MessageTracker(10);
        Msg original = msg("t1", "s1", "h1");
        original.setTimestamp(5000);
        tracker.onInsert(original, "t1");

        Msg answer = msg("t1", "s2", "h2");
        answer.setInAnswerTo(original.getMsgId());
        answer.setTimestamp(4000);
        tracker.onInsert(answer, "t1");

        MessageInfo info = tracker.getMessagesNewestFirst().get(0);
        assertNull(info.rtt, "negative RTT (clock skew) must not be recorded");
        assertEquals(0, tracker.getStats().getRttCount("t1"));
    }

    @Test
    void answersDoNotPolluteSenderStatsAndEvictionCleansCorrelationMaps() {
        MessageTracker tracker = new MessageTracker(1);
        Msg first = msg("t1", "s1", "h1");
        tracker.onInsert(first, "t1");
        Msg second = msg("t2", "s2", "h2");
        tracker.onInsert(second, "t2"); // evicts first

        Msg lateAnswer = msg("t1", "answerer", "answerHost");
        lateAnswer.setInAnswerTo(first.getMsgId());
        tracker.onInsert(lateAnswer, "t1"); // correlation target evicted -> ignored

        assertEquals(3, tracker.getStats().getTotalMessages());
        assertEquals(1, tracker.getStats().getTotalAnswers());
        assertEquals(0, tracker.getStats().getSenderCount("answerer"), "answers must not count as sender activity");
        assertEquals(0, tracker.getStats().getRttCount("t1"), "evicted request must not correlate");
        assertEquals(1, tracker.getMessagesNewestFirst().size());
    }
}

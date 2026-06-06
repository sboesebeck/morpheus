package de.caluga.morpheus.core;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TopicStatsTest {

    private Msg req(String topic, String sender) {
        Msg m = new Msg(topic, "m", "v", 30000);
        m.setMsgId(new MorphiumId());
        m.setSender(sender);
        m.setSenderHost("h");
        m.setTimestamp(System.currentTimeMillis());
        return m;
    }

    @Test
    void perTopicMessageCountAndOrdering() {
        MessageStats s = new MessageStats();
        s.recordTopicMessage("a");
        s.recordTopicMessage("a");
        s.recordTopicMessage("b");

        List<MessageStats.TopicAggregate> top = s.getTopicStats(10);
        assertEquals("a", top.get(0).topic());
        assertEquals(2, top.get(0).messages());
        assertEquals("b", top.get(1).topic());
        assertEquals(1, top.get(1).messages());
    }

    @Test
    void topicAggregateCombinesAnswersAndTimeouts() {
        MessageStats s = new MessageStats();
        s.recordTopicMessage("t");
        s.recordTopicMessage("t");
        s.recordRtt("t", 100);
        s.recordRtt("t", 300);
        s.recordTopicTimeout("t");

        MessageStats.TopicAggregate a = s.getTopicStats(10).get(0);
        assertEquals(2, a.messages());
        assertEquals(2, a.answers());
        assertEquals(200, a.avgRtt());
        assertEquals(1, a.timeouts());
    }

    @Test
    void nullOrEmptyTopicIsIgnored() {
        MessageStats s = new MessageStats();
        s.recordTopicMessage(null);
        s.recordTopicMessage("");
        s.recordTopicTimeout(null);
        assertTrue(s.getTopicStats(10).isEmpty());
    }

    @Test
    void trackerCountsTopicOnInsertAndTimeout() {
        MessageTracker tracker = new MessageTracker(10);
        Msg old = req("orders", "s1");
        old.setTimestamp(System.currentTimeMillis() - 10_000);
        tracker.onInsert(old, "orders");
        tracker.onInsert(req("orders", "s2"), "orders");

        tracker.markTimeouts(System.currentTimeMillis(), 2000);

        MessageStats.TopicAggregate a = tracker.getStats().getTopicStats(10).get(0);
        assertEquals("orders", a.topic());
        assertEquals(2, a.messages());
        assertEquals(1, a.timeouts());
    }
}

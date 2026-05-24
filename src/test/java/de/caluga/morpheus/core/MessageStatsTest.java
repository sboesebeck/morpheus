package de.caluga.morpheus.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MessageStatsTest {

    @Test
    void countsMessagesAnswersUpdatesTimeouts() {
        MessageStats stats = new MessageStats();
        stats.recordMessage("sender1", "host1");
        stats.recordMessage("sender1", "host2");
        stats.recordAnswer();
        stats.recordUpdate();
        stats.recordTimeouts(3);

        assertEquals(2, stats.getTotalMessages());
        assertEquals(1, stats.getTotalAnswers());
        assertEquals(1, stats.getTotalUpdates());
        assertEquals(3, stats.getTotalTimeouts());
        assertEquals(2, stats.getSenderCount("sender1"));
        assertEquals(1, stats.getHostCount("host1"));
    }

    @Test
    void aggregatesRttPerTopic() {
        MessageStats stats = new MessageStats();
        stats.recordRtt("order.created", 100);
        stats.recordRtt("order.created", 300);
        stats.recordRtt("cache.sync", 10);

        assertEquals(200, stats.getAvgRtt("order.created"));
        assertEquals(2, stats.getRttCount("order.created"));
        assertEquals(10, stats.getAvgRtt("cache.sync"));
    }

    @Test
    void topSlowestTopicsSortedDescending() {
        MessageStats stats = new MessageStats();
        stats.recordRtt("fast", 5);
        stats.recordRtt("slow", 500);
        stats.recordRtt("medium", 50);

        List<MessageStats.TopicRtt> top = stats.getSlowestTopics(2);
        assertEquals(2, top.size());
        assertEquals("slow", top.get(0).topic());
        assertEquals(500, top.get(0).avgRtt());
        assertEquals("medium", top.get(1).topic());
    }

    @Test
    void topActiveHostsAndSenders() {
        MessageStats stats = new MessageStats();
        stats.recordMessage("a", "h1");
        stats.recordMessage("a", "h1");
        stats.recordMessage("b", "h2");

        var hosts = stats.getTopHosts(5);
        assertEquals("h1", hosts.get(0).name());
        assertEquals(2, hosts.get(0).count());

        var senders = stats.getTopSenders(1);
        assertEquals(1, senders.size());
        assertEquals("a", senders.get(0).name());
    }
}

package de.caluga.morpheus.core;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PairCountTest {

    private Msg msg(String sender, String host) {
        Msg m = new Msg("t", "m", "v", 30000);
        m.setMsgId(new MorphiumId());
        m.setSender(sender);
        m.setSenderHost(host);
        m.setTimestamp(System.currentTimeMillis());
        return m;
    }

    @Test
    void recordsAndOrdersHostPairs() {
        MessageStats s = new MessageStats();
        s.recordPair("hermes", "wA", "app1", "h1", 10);
        s.recordPair("hermes", "wA", "app1", "h1", 30);
        s.recordPair("hermes", "wB", "app1", "h2", 50);

        List<MessageStats.PairCount> byHost = s.getTopPairs(true, 10);
        assertEquals("app1", byHost.get(0).from());
        assertEquals("h1", byHost.get(0).to());
        assertEquals(2, byHost.get(0).count());
        assertEquals(20, byHost.get(0).avgRtt());
        assertEquals("h2", byHost.get(1).to());
        assertEquals(1, byHost.get(1).count());
    }

    @Test
    void senderAndHostKeyedSeparately() {
        MessageStats s = new MessageStats();
        s.recordPair("hermes", "wA", "app1", "h1", 10);
        assertEquals("hermes", s.getTopPairs(false, 10).get(0).from());
        assertEquals("wA", s.getTopPairs(false, 10).get(0).to());
        assertEquals("app1", s.getTopPairs(true, 10).get(0).from());
    }

    @Test
    void correlateAnswerRecordsThePair() {
        MessageTracker tracker = new MessageTracker(10);
        Msg request = msg("hermes", "app1");
        request.setTimestamp(1000);
        tracker.onInsert(request, "t");

        Msg answer = msg("worker", "h9");
        answer.setInAnswerTo(request.getMsgId());
        answer.setTimestamp(1200);
        tracker.onInsert(answer, "t");

        MessageStats.PairCount hostPair = tracker.getStats().getTopPairs(true, 10).get(0);
        assertEquals("app1", hostPair.from());
        assertEquals("h9", hostPair.to());
        assertEquals(1, hostPair.count());
        assertEquals(200, hostPair.avgRtt());

        MessageStats.PairCount senderPair = tracker.getStats().getTopPairs(false, 10).get(0);
        assertEquals("hermes", senderPair.from());
        assertEquals("worker", senderPair.to());
    }
}

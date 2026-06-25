package de.caluga.morpheus.core;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MessageFilterTest {

    private MessageInfo info(String topic, String sender, String host) {
        Msg m = new Msg(topic, "body-" + topic, "v", 30000);
        m.setMsgId(new MorphiumId());
        m.setSender(sender); m.setSenderHost(host);
        m.setTimestamp(System.currentTimeMillis());
        return new MessageInfo(m, topic);
    }

    @Test
    void emptyFilterAcceptsEverything() {
        MessageFilter f = new MessageFilter();
        assertTrue(f.isEmpty());
        assertTrue(f.accepts(info("orders", "a", "h1")));
    }

    @Test
    void orWithinAxisAndBetweenAxes() {
        MessageFilter f = new MessageFilter();
        f.topics.add("orders");
        f.topics.add("cache");          // OR within topic axis
        f.senders.add("a");             // AND against sender axis
        assertTrue(f.accepts(info("orders", "a", "h1")));
        assertTrue(f.accepts(info("cache", "a", "h1")));
        assertFalse(f.accepts(info("billing", "a", "h1")), "topic not in set");
        assertFalse(f.accepts(info("orders", "b", "h1")), "sender not in set");
    }

    @Test
    void hostAxisFilters() {
        MessageFilter f = new MessageFilter();
        f.hosts.add("h1");
        assertTrue(f.accepts(info("orders", "a", "h1")));
        assertFalse(f.accepts(info("orders", "a", "h2")));
    }

    @Test
    void answeredTriStateOnlyAndExclude() {
        MessageInfo answered = info("orders", "a", "h1");
        answered.rtt = 42L;
        MessageInfo open = info("orders", "a", "h1");   // rtt null

        MessageFilter only = new MessageFilter();
        only.answered = MessageFilter.TriState.ONLY;
        assertTrue(only.accepts(answered));
        assertFalse(only.accepts(open));

        MessageFilter excl = new MessageFilter();
        excl.answered = MessageFilter.TriState.EXCLUDE;
        assertFalse(excl.accepts(answered));
        assertTrue(excl.accepts(open));
    }

    @Test
    void timeoutExclusiveDeletedTriStates() {
        MessageInfo m = info("orders", "a", "h1");
        m.isTimedOut = true; m.isExclusive = true; m.isDeleted = true;

        MessageFilter f = new MessageFilter();
        f.timedOut = MessageFilter.TriState.ONLY;
        assertTrue(f.accepts(m));
        f.exclusive = MessageFilter.TriState.EXCLUDE;        // m IS exclusive → excluded
        assertFalse(f.accepts(m));
        f.exclusive = MessageFilter.TriState.NONE;
        f.deleted = MessageFilter.TriState.ONLY;
        assertTrue(f.accepts(m));
    }

    @Test
    void bodyRegexMatchesCaseInsensitive() {
        MessageFilter f = new MessageFilter();
        f.bodyRegex = "ORD.*";
        assertTrue(f.accepts(info("orders", "a", "h1")));    // body = "body-orders"
        assertFalse(f.accepts(info("cache", "a", "h1")));
    }

    @Test
    void invalidRegexIsInactiveAndReported() {
        MessageFilter f = new MessageFilter();
        f.bodyRegex = "[unclosed";
        assertFalse(f.regexValid());
        assertTrue(f.accepts(info("orders", "a", "h1")), "invalid regex must not filter anything out");
    }

    @Test
    void cycleAdvancesTriState() {
        MessageFilter f = new MessageFilter();
        assertEquals(MessageFilter.TriState.NONE, f.answered);
        f.cycle("answered"); assertEquals(MessageFilter.TriState.ONLY, f.answered);
        f.cycle("answered"); assertEquals(MessageFilter.TriState.EXCLUDE, f.answered);
        f.cycle("answered"); assertEquals(MessageFilter.TriState.NONE, f.answered);
    }

    @Test
    void clearResetsEverything() {
        MessageFilter f = new MessageFilter();
        f.topics.add("orders"); f.senders.add("a"); f.hosts.add("h1");
        f.bodyRegex = "x"; f.answered = MessageFilter.TriState.ONLY;
        f.clear();
        assertTrue(f.isEmpty());
    }
}

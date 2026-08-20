package de.caluga.morpheus.core;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import de.caluga.morphium.messaging.MsgLock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class MessageFeedTest {

    /** Records every resolution call; returns canned locks or throws. */
    static class FakeResolver implements MessageFeed.LockResolver {
        final Map<String, String> topicToCollection = new HashMap<>();
        final List<List<MorphiumId>> resolvedBatches = new ArrayList<>();
        final Set<MorphiumId> locked = new HashSet<>();
        RuntimeException failWith;

        @Override
        public String lockCollection(String topic) {
            return topicToCollection.getOrDefault(topic, "msg_lock_" + topic);
        }

        @Override
        public Map<MorphiumId, MsgLock> resolveLocks(String lockCollection, List<MorphiumId> ids) {
            if (failWith != null) throw failWith;
            resolvedBatches.add(List.copyOf(ids));
            Map<MorphiumId, MsgLock> found = new HashMap<>();
            for (var id : ids) {
                if (locked.contains(id)) {
                    MsgLock l = new MsgLock(id, new Date(123_000L));
                    l.setLockId("worker-1");
                    found.put(id, l);
                }
            }
            return found;
        }
    }

    private Msg msg(String topic) {
        Msg m = new Msg(topic, "m", "v", 30000);
        m.setMsgId(new MorphiumId());
        m.setSender("s1");
        m.setSenderHost("h1");
        m.setTimestamp(System.currentTimeMillis());
        return m;
    }

    private MessageInfo info(MessageTracker tracker, MorphiumId id) {
        return tracker.getMessagesNewestFirst().stream()
                .filter(i -> i.id.equals(id)).findFirst().orElseThrow();
    }

    private MessageFeed feed(MessageTracker tracker, FakeResolver resolver, int[] changeCount) {
        return new MessageFeed(null, null, tracker, () -> changeCount[0]++, false, resolver);
    }

    @Test
    void flushResolvesOneBatchPerLockCollection() {
        MessageTracker tracker = new MessageTracker(10);
        FakeResolver resolver = new FakeResolver();
        Msg m1 = msg("t1");
        Msg m2 = msg("t1");
        Msg m3 = msg("t2");
        tracker.onInsert(m1, "t1");
        tracker.onInsert(m2, "t1");
        tracker.onInsert(m3, "t2");

        MessageFeed feed = feed(tracker, resolver, new int[1]);
        feed.queueLockLookup(m1.getMsgId(), "t1");
        feed.queueLockLookup(m2.getMsgId(), "t1");
        feed.queueLockLookup(m3.getMsgId(), "t2");
        resolver.locked.add(m2.getMsgId());          // only m2 currently locked

        feed.flushPendingLocks();

        assertEquals(2, resolver.resolvedBatches.size(), "one batch per lock collection, not one per id");
        assertNull(info(tracker, m1.getMsgId()).lockedBy);
        assertNull(info(tracker, m3.getMsgId()).lockedBy);
        assertEquals("worker-1", info(tracker, m2.getMsgId()).lockedBy);
    }

    @Test
    void flushSetsAndClearsLockStatus() {
        MessageTracker tracker = new MessageTracker(10);
        FakeResolver resolver = new FakeResolver();
        Msg m1 = msg("t1");
        Msg m2 = msg("t1");
        tracker.onInsert(m1, "t1");
        tracker.onInsert(m2, "t1");

        MessageFeed feed = feed(tracker, resolver, new int[1]);
        feed.queueLockLookup(m1.getMsgId(), "t1");
        feed.queueLockLookup(m2.getMsgId(), "t1");
        resolver.locked.add(m2.getMsgId());

        feed.flushPendingLocks();

        assertNull(info(tracker, m1.getMsgId()).lockedBy, "unlocked id must have its lock status cleared");
        assertEquals("worker-1", info(tracker, m2.getMsgId()).lockedBy);
        assertEquals(123_000L, info(tracker, m2.getMsgId()).lockedUntil);
    }

    @Test
    void duplicateQueuesCoalesceToLatestTopic() {
        MessageTracker tracker = new MessageTracker(10);
        FakeResolver resolver = new FakeResolver();
        resolver.topicToCollection.put("tA", "lock_A");
        resolver.topicToCollection.put("tB", "lock_B");
        Msg m = msg("tA");
        tracker.onInsert(m, "tA");

        MessageFeed feed = feed(tracker, resolver, new int[1]);
        feed.queueLockLookup(m.getMsgId(), "tA");
        feed.queueLockLookup(m.getMsgId(), "tB");     // superseded before the flush

        feed.flushPendingLocks();

        assertEquals(1, resolver.resolvedBatches.size());
        assertEquals(1, resolver.resolvedBatches.get(0).size(), "the same id must not be resolved twice");
        assertNull(info(tracker, m.getMsgId()).lockedBy);
    }

    @Test
    void resolverFailureDisablesLockLookups() {
        MessageTracker tracker = new MessageTracker(10);
        FakeResolver resolver = new FakeResolver();
        resolver.failWith = new RuntimeException("backend gone");
        Msg m = msg("t1");
        tracker.onInsert(m, "t1");

        int[] changeCount = new int[1];
        MessageFeed feed = feed(tracker, resolver, changeCount);
        feed.queueLockLookup(m.getMsgId(), "t1");
        feed.flushPendingLocks();                      // first failure flips the latch

        int callsAfterFailure = resolver.resolvedBatches.size();
        Msg m2 = msg("t1");
        tracker.onInsert(m2, "t1");
        feed.queueLockLookup(m2.getMsgId(), "t1");
        feed.flushPendingLocks();

        assertEquals(callsAfterFailure, resolver.resolvedBatches.size(),
                "disabled lookup must not keep hammering the database");
        assertNull(info(tracker, m2.getMsgId()).lockedBy);
    }

    @Test
    void flushWithNothingPendingIsNoOp() {
        MessageTracker tracker = new MessageTracker(10);
        FakeResolver resolver = new FakeResolver();
        int[] changeCount = new int[1];
        MessageFeed feed = feed(tracker, resolver, changeCount);

        feed.flushPendingLocks();

        assertEquals(0, resolver.resolvedBatches.size());
        assertEquals(0, changeCount[0], "no change notification without work");
    }

    @Test
    void flushNotifiesOnChange() {
        MessageTracker tracker = new MessageTracker(10);
        FakeResolver resolver = new FakeResolver();
        Msg m = msg("t1");
        tracker.onInsert(m, "t1");
        int[] changeCount = new int[1];
        MessageFeed feed = feed(tracker, resolver, changeCount);
        feed.queueLockLookup(m.getMsgId(), "t1");

        feed.flushPendingLocks();

        assertEquals(1, changeCount[0]);
    }
}

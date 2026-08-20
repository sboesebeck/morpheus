package de.caluga.morpheus.core;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.changestream.ChangeStreamEvent;
import de.caluga.morphium.changestream.ChangeStreamListener;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.MorphiumMessaging;
import de.caluga.morphium.messaging.Msg;
import de.caluga.morphium.messaging.MsgLock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Subscribes to the database change stream and feeds message events into a
 * MessageTracker. Pure adapter: classification, V5/V6 topic extraction,
 * deserialization, lock status resolution. onChange runs after every
 * processed event.
 *
 * <p>Lock lookups are batched: events enqueue the (id, topic) pair, a small
 * background thread flushes them every {@link #LOCK_BATCH_MS} with one query
 * per lock collection instead of one findById round trip per event.
 *
 * <p>Lifecycle: {@link #start()} watches asynchronously and can be stopped
 * with {@link #stop()}; {@link #watch()} keeps the old blocking behavior for
 * process-lifetime viewers (e.g. the watch command).
 */
public class MessageFeed {
    /** Flush interval for batched lock lookups. */
    static final long LOCK_BATCH_MS = 50;

    private final Morphium morphium;
    private final MorphiumMessaging messaging;
    private final MessageTracker tracker;
    private final Runnable onChange;
    private final boolean verbose;
    private final LockResolver lockResolver;

    /** Set true after the first lock lookup failure so a broken query can't flood the monitor. */
    private volatile boolean lockLookupDisabled = false;

    /** Non-answer inserts/updates awaiting a lock status lookup, newest topic wins per id. */
    private final Map<MorphiumId, String> pendingLockTopics = new ConcurrentHashMap<>();

    private final AtomicReference<AtomicBoolean> runningFlag = new AtomicReference<>();
    private volatile Thread lockBatcher;
    private volatile boolean lockBatcherActive;

    /**
     * Lock state lookup, abstracted for tests. Production: one $in query per lock
     * collection with a per-id fallback (same access paths the messaging layer uses).
     */
    interface LockResolver {
        /** Lock collection for a message topic. */
        String lockCollection(String topic);

        /** Only currently existing locks, keyed by message id. Throws on hard failure. */
        Map<MorphiumId, MsgLock> resolveLocks(String lockCollection, List<MorphiumId> ids);
    }

    public MessageFeed(Morphium morphium, MorphiumMessaging messaging,
                       MessageTracker tracker, Runnable onChange, boolean verbose) {
        this(morphium, messaging, tracker, onChange, verbose, dbResolver(morphium, messaging));
    }

    /** Test seam: inject the lock lookup. */
    MessageFeed(Morphium morphium, MorphiumMessaging messaging,
                MessageTracker tracker, Runnable onChange, boolean verbose, LockResolver lockResolver) {
        this.morphium = morphium;
        this.messaging = messaging;
        this.tracker = tracker;
        this.onChange = onChange;
        this.verbose = verbose;
        this.lockResolver = lockResolver;
    }

    private static LockResolver dbResolver(Morphium morphium, MorphiumMessaging messaging) {
        return new LockResolver() {
            @Override
            public String lockCollection(String topic) {
                try {
                    return messaging.getLockCollectionName(topic);
                } catch (Exception e) {
                    return messaging.getLockCollectionName();
                }
            }

            @Override
            public Map<MorphiumId, MsgLock> resolveLocks(String lockCollection, List<MorphiumId> ids) {
                try {
                    // Same access path the messaging layer itself uses for locks
                    // (queries MsgLock via f("_id") on the lock collection).
                    var query = morphium.createQueryFor(MsgLock.class, lockCollection).f("_id").in(ids);
                    Map<MorphiumId, MsgLock> found = new HashMap<>();
                    for (MsgLock lock : query.asList()) {
                        if (lock != null && lock.getId() != null) {
                            found.put(lock.getId(), lock);
                        }
                    }
                    return found;
                } catch (Exception e) {
                    // Batch query unavailable (e.g. driver quirk) - degrade to one lookup per id.
                    Map<MorphiumId, MsgLock> found = new HashMap<>();
                    for (var id : ids) {
                        MsgLock lock = morphium.findById(MsgLock.class, id, lockCollection);
                        if (lock != null) found.put(id, lock);
                    }
                    return found;
                }
            }
        };
    }

    /** Starts watching in the background (morphium's async pool). Stop with {@link #stop()}. */
    public void start() {
        if (runningFlag.get() != null || lockBatcher != null) {
            throw new IllegalStateException("feed already running");
        }
        runningFlag.set(morphium.watchDbAsync(true, (List<Map<String, Object>>) null, listener()));
        startLockBatcher();
    }

    /**
     * Stops the change stream and the lock batcher. Idempotent. Stopping the
     * watch loop is cooperative: it ends once the current change stream poll
     * returns, so this may take up to the stream's maxWaitTime.
     */
    public void stop() {
        AtomicBoolean flag = runningFlag.getAndSet(null);
        if (flag != null) flag.set(false);
        stopLockBatcher();
    }

    public boolean isRunning() {
        AtomicBoolean flag = runningFlag.get();
        return flag != null && flag.get();
    }

    /** Blocks while watching (process-lifetime viewers like the watch command). */
    public void watch() {
        startLockBatcher();
        try {
            morphium.watchDb(true, listener());
        } finally {
            stopLockBatcher();
        }
    }

    private void startLockBatcher() {
        lockBatcherActive = true;
        Thread b = new Thread(this::lockBatchLoop, "msgfeed-lock-batcher");
        b.setDaemon(true);
        b.start();
        lockBatcher = b;
    }

    private void stopLockBatcher() {
        lockBatcherActive = false;
        Thread b = lockBatcher;
        lockBatcher = null;
        if (b != null) {
            b.interrupt();
            try {
                b.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private ChangeStreamListener listener() {
        return new ChangeStreamListener() {
            @Override
            public boolean incomingData(ChangeStreamEvent evt) {
                try {
                    processEvent(evt);
                } catch (Exception e) {
                    if (verbose) {
                        System.err.println("Error processing change stream event: " + e.getMessage());
                        e.printStackTrace(System.err);
                    }
                }
                return true;
            }
        };
    }

    void processEvent(ChangeStreamEvent evt) {
        if (evt.getOperationType().equals("delete")) {
            // Old code (line 359): cast getDocumentKey() to Map, then get "_id"
            Object docIdObj = ((Map) evt.getDocumentKey()).get("_id");
            if (docIdObj instanceof MorphiumId docId) {
                pendingLockTopics.remove(docId);
                tracker.onDelete(docId);
                onChange.run();
            }
            return;
        }

        Map<String, Object> doc = evt.getFullDocument();
        if (doc == null) {
            // Old code (line 380): getDocumentKey() directly as MorphiumId (no Map cast)
            Object docIdObj = evt.getDocumentKey();
            if (docIdObj instanceof MorphiumId docId) {
                Msg found = morphium.findById(Msg.class, docId);
                if (found != null) {
                    doc = morphium.getMapper().serialize(found);
                }
            }
        }
        if (doc == null) return;
        if (!doc.containsKey("name") && !doc.containsKey("topic")) return;

        Msg msg = morphium.getMapper().deserialize(Msg.class, doc);
        if (msg == null || msg.getMsgId() == null) return;

        // V6 uses 'topic', V5 used 'name'
        String topic = (String) doc.getOrDefault("topic", doc.get("name"));

        switch (evt.getOperationType()) {
            case "insert" -> {
                tracker.onInsert(msg, topic);
                if (!msg.isAnswer()) {
                    queueLockLookup(msg.getMsgId(), topic);
                }
                onChange.run();
            }
            case "update", "replace" -> {
                tracker.onUpdate(msg);
                queueLockLookup(msg.getMsgId(), topic);
                onChange.run();
            }
            default -> { /* ignore */ }
        }
    }

    /** Enqueues a lock status lookup; coalesced with others in the next flush window. */
    void queueLockLookup(MorphiumId id, String topic) {
        if (lockLookupDisabled) return;
        pendingLockTopics.put(id, topic);
    }

    private void lockBatchLoop() {
        while (lockBatcherActive && !Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(LOCK_BATCH_MS);
            } catch (InterruptedException e) {
                return;
            }
            flushPendingLocks();
        }
    }

    /**
     * Drains the pending queue and resolves one batch with a single query per
     * lock collection: ids with a lock get the lock state, all others get
     * their status cleared.
     */
    void flushPendingLocks() {
        if (lockLookupDisabled || pendingLockTopics.isEmpty()) return;

        Map<MorphiumId, String> batch = new HashMap<>();
        for (var id : List.copyOf(pendingLockTopics.keySet())) {
            String topic = pendingLockTopics.remove(id);
            if (topic != null) batch.put(id, topic);
        }
        if (batch.isEmpty()) return;

        Map<String, List<MorphiumId>> byCollection = new HashMap<>();
        for (var e : batch.entrySet()) {
            String coll = lockResolver.lockCollection(e.getValue());
            if (coll == null) continue;
            byCollection.computeIfAbsent(coll, k -> new ArrayList<>()).add(e.getKey());
        }

        try {
            for (var e : byCollection.entrySet()) {
                var locks = lockResolver.resolveLocks(e.getKey(), e.getValue());
                for (var id : e.getValue()) {
                    MsgLock lock = locks.get(id);
                    if (lock != null) {
                        Long until = lock.getDeleteAt() != null ? lock.getDeleteAt().getTime() : null;
                        tracker.setLockStatus(id, lock.getLockId(), until);
                    } else {
                        tracker.setLockStatus(id, null, null);
                    }
                }
            }
        } catch (Exception ex) {
            // Disable after the first hard failure so a broken lookup can't flood the
            // full-screen monitor with one error line per message.
            lockLookupDisabled = true;
            if (verbose) {
                System.err.println("Lock lookup unavailable, disabled for this session: " + ex.getMessage());
            }
        }
        onChange.run();
    }
}

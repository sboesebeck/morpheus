package de.caluga.morpheus.core;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.changestream.ChangeStreamEvent;
import de.caluga.morphium.changestream.ChangeStreamListener;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.MorphiumMessaging;
import de.caluga.morphium.messaging.Msg;
import de.caluga.morphium.messaging.MsgLock;

import java.util.Map;

/**
 * Subscribes to the database change stream and feeds message events into a
 * MessageTracker. Pure adapter: classification, V5/V6 topic extraction,
 * deserialization, lock lookup. onChange runs after every processed event.
 */
public class MessageFeed {
    private final Morphium morphium;
    private final MorphiumMessaging messaging;
    private final MessageTracker tracker;
    private final Runnable onChange;
    private final boolean verbose;

    /** Set true after the first lock lookup failure so a broken query can't flood the monitor. */
    private boolean lockLookupDisabled = false;

    public MessageFeed(Morphium morphium, MorphiumMessaging messaging,
                       MessageTracker tracker, Runnable onChange, boolean verbose) {
        this.morphium = morphium;
        this.messaging = messaging;
        this.tracker = tracker;
        this.onChange = onChange;
        this.verbose = verbose;
    }

    /** Blocks while watching (same behavior as the old monitor's watchDb call). */
    public void watch() {
        morphium.watchDb(true, new ChangeStreamListener() {
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
        });
    }

    void processEvent(ChangeStreamEvent evt) {
        if (evt.getOperationType().equals("delete")) {
            // Old code (line 359): cast getDocumentKey() to Map, then get "_id"
            Object docIdObj = ((Map) evt.getDocumentKey()).get("_id");
            if (docIdObj instanceof MorphiumId docId) {
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
                    resolveLockStatus(msg.getMsgId(), topic);
                }
                onChange.run();
            }
            case "update", "replace" -> {
                tracker.onUpdate(msg);
                resolveLockStatus(msg.getMsgId(), topic);
                onChange.run();
            }
            default -> { /* ignore */ }
        }
    }

    private void resolveLockStatus(MorphiumId id, String topic) {
        if (lockLookupDisabled) {
            return;
        }
        try {
            // Query the lock collection via Morphium's mapped MsgLock entity, exactly as the
            // messaging layer does internally. Map.class has no mapped _id field, which is why
            // the old `createQueryFor(Map.class,...).f("_id")` threw "field is null" per event.
            // MsgLock fields: lockId = who holds the lock, deleteAt = lock expiry (may be null).
            String lockCollection = messaging.getLockCollectionName(topic);
            MsgLock lock = morphium.findById(MsgLock.class, id, lockCollection);
            if (lock != null) {
                Long until = lock.getDeleteAt() != null ? lock.getDeleteAt().getTime() : null;
                tracker.setLockStatus(id, lock.getLockId(), until);
            } else {
                tracker.setLockStatus(id, null, null);
            }
        } catch (Exception e) {
            // Disable after the first failure so a broken lookup can't flood the full-screen
            // monitor with one error line per message.
            lockLookupDisabled = true;
            if (verbose) {
                System.err.println("Lock lookup unavailable, disabled for this session: " + e.getMessage());
            }
        }
    }
}

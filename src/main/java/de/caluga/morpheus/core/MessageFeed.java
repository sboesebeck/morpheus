package de.caluga.morpheus.core;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.changestream.ChangeStreamEvent;
import de.caluga.morphium.changestream.ChangeStreamListener;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.MorphiumMessaging;
import de.caluga.morphium.messaging.Msg;

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
        try {
            String lockCollection = messaging.getLockCollectionName(topic);
            var lockQuery = morphium.createQueryFor(Map.class, lockCollection).f("_id").eq(id);
            Map<String, Object> lockDoc = lockQuery.get();
            if (lockDoc != null) {
                Long until = null;
                Object u = lockDoc.get("locked_until");
                if (u instanceof Number n) {
                    until = n.longValue();
                }
                tracker.setLockStatus(id, (String) lockDoc.get("locked_by"), until);
            } else {
                tracker.setLockStatus(id, null, null);
            }
        } catch (Exception e) {
            if (verbose) {
                System.err.println("Lock query failed: " + e.getMessage());
            }
        }
    }
}

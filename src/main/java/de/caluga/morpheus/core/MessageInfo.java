package de.caluga.morpheus.core;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;

/** Tracked state of one observed message. Mutable; guarded by MessageTracker's lock. */
public class MessageInfo {
    public MorphiumId id;
    public String sender;
    public String senderHost;
    public String recipient;
    public String topic;
    public int size;
    public long timestamp;
    public boolean isAnswer;
    public int processedByCount;
    public String lockedBy;
    public Long lockedUntil;
    public String answeredBy;
    public String answeredByHost;
    public boolean isExclusive;
    public Long rtt;
    public int updateCount = 1;
    public boolean isTimedOut;
    public boolean isDeleteAfterProcessing;
    public int deleteAfterProcessingTime;
    public boolean isV5;
    public boolean answerIsV5;
    public boolean isDeleted;

    public MessageInfo(Msg msg, String topic) {
        this.id = msg.getMsgId();
        this.sender = msg.getSender();
        this.senderHost = msg.getSenderHost();
        this.recipient = msg.getRecipients() != null && !msg.getRecipients().isEmpty()
                         ? msg.getRecipients().get(0) : "";
        this.topic = topic;
        this.size = calculateSize(msg);
        this.timestamp = msg.getTimestamp();
        this.isAnswer = msg.isAnswer();
        this.isExclusive = msg.isExclusive();
        this.processedByCount = msg.getProcessedBy() != null ? msg.getProcessedBy().size() : 0;
        this.isDeleteAfterProcessing = msg.isDeleteAfterProcessing();
        this.deleteAfterProcessingTime = msg.getDeleteAfterProcessingTime();
        this.isV5 = detectV5(msg);
    }

    static boolean detectV5(Msg msg) {
        try {
            return msg.getTopic() == null || msg.getTopic().isEmpty();
        } catch (Exception e) {
            return true;
        }
    }

    public void update(Msg msg) {
        if (msg.getProcessedBy() != null) {
            int newCount = msg.getProcessedBy().size();
            if (newCount > this.processedByCount) {
                this.processedByCount = newCount;
                this.updateCount++;
            }
        }
    }

    private int calculateSize(Msg msg) {
        int s = 0;
        if (msg.getMsg() != null) s += msg.getMsg().length();
        if (msg.getMapValue() != null) s += msg.getMapValue().size() * 50;
        if (msg.getSender() != null) s += msg.getSender().length();
        try {
            if (msg.getTopic() != null) s += msg.getTopic().length();
        } catch (Exception e) {
            // V5 message without topic
        }
        return s;
    }
}

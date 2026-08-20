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
    public String body;

    static final int MAX_BODY_CAPTURE = 2048;
    private static final int MAX_TRUNCATE_LENGTH = 50;

    public MessageInfo(final Msg msg, final String topic) {
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
        this.body = captureBody(msg);
    }

    /** Joins msg text + value + mapValue into a single searchable string, capped to MAX_BODY_CAPTURE. */
    static String captureBody(final Msg msg) {
        final StringBuilder sb = new StringBuilder();
        try {
            if (msg.getMsg() != null) {
                sb.append(msg.getMsg());
            }
        } catch (final Exception ignored) {
        }
        try {
            if (msg.getValue() != null) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(msg.getValue());
            }
        } catch (final Exception ignored) {
        }
        try {
            if (msg.getMapValue() != null && !msg.getMapValue().isEmpty()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(msg.getMapValue());
            }
        } catch (final Exception ignored) {
        }
        final String s = sb.toString();
        return s.length() > MAX_BODY_CAPTURE ? s.substring(0, MAX_BODY_CAPTURE) : s;
    }

    static boolean detectV5(final Msg msg) {
        try {
            return msg.getTopic() == null || msg.getTopic().isEmpty();
        } catch (final Exception e) {
            return true;
        }
    }

    public void update(final Msg msg) {
        if (msg.getProcessedBy() != null) {
            final int newCount = msg.getProcessedBy().size();
            if (newCount > this.processedByCount) {
                this.processedByCount = newCount;
                this.updateCount++;
            }
        }
    }

    private int calculateSize(final Msg msg) {
        int s = 0;
        if (msg.getMsg() != null) {
            s += msg.getMsg().length();
        }
        if (msg.getMapValue() != null) {
            s += msg.getMapValue().size() * MAX_TRUNCATE_LENGTH;
        }
        if (msg.getSender() != null) {
            s += msg.getSender().length();
        }
        try {
            if (msg.getTopic() != null) {
                s += msg.getTopic().length();
            }
        } catch (final Exception e) {
            // V5 message without topic
        }
        return s;
    }
}

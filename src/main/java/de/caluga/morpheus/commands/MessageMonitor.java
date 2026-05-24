package de.caluga.morpheus.commands;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import de.caluga.morpheus.ICommand;
import de.caluga.morpheus.Morpheus;
import de.caluga.morpheus.utils.TerminalUtils;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.changestream.ChangeStreamEvent;
import de.caluga.morphium.changestream.ChangeStreamListener;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;

public class MessageMonitor implements ICommand {
    public final static String NAME = "monitor";
    public final static String DESCRIPTION = "messaging monitor, will show messages as they are being sent and answered. Use --verbose for RTT debug info";

    private int countColWidth = 5;
    private int senderColWidth;
    private int recipientColWidth;
    private int nameColWidth;
    private int sizeColWidth = 8;
    private int processedColWidth;
    private int answerColWidth = 6;
    private int rttColWidth = 11;

    @Override
    public void execute(Morpheus morpheus, Map<String, String> args) throws Exception {
        // Get terminal size and calculate column widths
        boolean verbose = args.containsKey("--verbose");
        TerminalUtils.Size termSize = TerminalUtils.getTerminalSize(verbose);
        int termWidth = termSize.getCol();

        if (verbose) {
            morpheus.pr("[c3]Terminal size detected: " + termWidth + " cols x " + termSize.getRow() + " rows[r]");
        }

        // Calculate column widths
        // Fixed columns: # (5) + Size (8) + Answer (6) + RTT (11) = 30
        // Separators: 7 pipes with spaces = 14 chars
        // Safety margin: 8 chars to prevent overflow
        int fixedWidth = countColWidth + sizeColWidth + answerColWidth + rttColWidth + 14 + 8;
        int availableWidth = termWidth - fixedWidth;

        // Distribute remaining width: Sender 25%, Recipient 25%, Name 30%, ProcessedBy 20%
        senderColWidth = (int) (availableWidth * 0.25);
        recipientColWidth = (int) (availableWidth * 0.25);
        nameColWidth = (int) (availableWidth * 0.30);
        processedColWidth = availableWidth - senderColWidth - recipientColWidth - nameColWidth;

        // Minimum widths
        if (senderColWidth < 15) senderColWidth = 15;
        if (recipientColWidth < 15) recipientColWidth = 15;
        if (nameColWidth < 20) nameColWidth = 20;
        if (processedColWidth < 12) processedColWidth = 12;

        // Map to track message timestamps for roundtrip calculation
        // Note: Negative RTTs can occur due to:
        // 1. Clock skew between different nodes
        // 2. MongoDB change stream ordering (answer arrives before original message)
        // 3. Starting monitor mid-stream (answers without seeing original messages)
        final ConcurrentHashMap<MorphiumId, Long> messageTimestamps = new ConcurrentHashMap<>();

        Morphium m = morpheus.getMorphium();
        m.watch(Msg.class, true, new ChangeStreamListener() {
            private int count = 0;

            @Override
            public boolean incomingData(ChangeStreamEvent evt) {
                count++;

                // Print header every 50 messages
                if (count % 50 == 1) {
                    printHeader(morpheus);
                }

                if (evt.getOperationType().equals("delete")) return true;

                if (evt.getFullDocument() != null) {
                    Msg msg = morpheus.getMorphium().getMapper().deserialize(Msg.class, evt.getFullDocument());

                    // Track message timestamp for roundtrip calculation
                    if (!msg.isAnswer() && msg.getMsgId() != null) {
                        messageTimestamps.put(msg.getMsgId(), msg.getTimestamp());
                    }

                    // Alternate row colors for readability
                    String color = (count % 2 == 0) ? "[c3]" : "";

                    // Format row data
                    String countStr = padRight("" + count, countColWidth);
                    String sender = truncate(msg.getSender(), senderColWidth);
                    String recipient = truncate(
                        msg.getRecipients() != null && !msg.getRecipients().isEmpty()
                            ? msg.getRecipients().get(0)
                            : "",
                        recipientColWidth
                    );
                    String topic = truncate(msg.getTopic(), nameColWidth);

                    // Calculate message size
                    String size = formatSize(calculateSize(msg));

                    // Get processed by info
                    String processedBy = "";
                    if (msg.getProcessedBy() != null && !msg.getProcessedBy().isEmpty()) {
                        processedBy = truncate(msg.getProcessedBy().get(0), processedColWidth);
                    }

                    // Answer status
                    String answer = msg.isAnswer() ? "[good]YES[r]" : "   ";

                    // Calculate roundtrip time for answers
                    String rtt = "";
                    if (msg.isAnswer() && msg.getInAnswerTo() != null) {
                        Long originalTime = messageTimestamps.get(msg.getInAnswerTo());
                        if (originalTime != null) {
                            long roundtripMs = msg.getTimestamp() - originalTime;

                            // Only show RTT if positive (negative indicates clock skew or ordering issues)
                            if (roundtripMs >= 0) {
                                rtt = formatRoundtrip(roundtripMs);
                            } else if (verbose) {
                                // Debug: show negative RTT in verbose mode
                                morpheus.pr("[warning]DEBUG: Negative RTT detected: " + roundtripMs + "ms " +
                                    "(answer timestamp: " + msg.getTimestamp() + ", " +
                                    "original timestamp: " + originalTime + ")[r]");
                                rtt = "[warning]<0[r]";
                            }

                            // Clean up old entry
                            messageTimestamps.remove(msg.getInAnswerTo());
                        } else if (msg.isAnswer() && verbose) {
                            // Debug: answer arrived but we never saw the original message
                            morpheus.pr("[warning]DEBUG: Answer without tracked original message. " +
                                "InAnswerTo: " + msg.getInAnswerTo() + "[r]");
                        }
                    }

                    morpheus.pr(String.format("%s%s | %s | %s | %s | %s | %s | %s | %s[r]",
                        color,
                        countStr,
                        padRight(sender, senderColWidth),
                        padRight(recipient, recipientColWidth),
                        padRight(topic, nameColWidth),
                        padRight(size, sizeColWidth),
                        padRight(processedBy, processedColWidth),
                        padRight(answer, answerColWidth),
                        padRight(rtt, rttColWidth)
                    ));
                } else {
                    return true;
                }

                return true;
            }

            private void printHeader(Morpheus morpheus) {
                String separator = "─".repeat(termWidth - 1);
                morpheus.pr("[c2]" + separator + "[r]");
                morpheus.pr(String.format("[header1]%s | %s | %s | %s | %s | %s | %s | %s[r]",
                    padRight("#", countColWidth),
                    padRight("Sender", senderColWidth),
                    padRight("Recipient", recipientColWidth),
                    padRight("Name", nameColWidth),
                    padRight("Size", sizeColWidth),
                    padRight("Processed", processedColWidth),
                    padRight("Ans", answerColWidth),
                    padRight("RTT", rttColWidth)
                ));
                morpheus.pr("[c2]" + separator + "[r]");
            }

            private int calculateSize(Msg msg) {
                int size = 0;
                // Estimate size based on message content
                if (msg.getMsg() != null) {
                    size += msg.getMsg().length();
                }
                if (msg.getMapValue() != null) {
                    // Rough estimate: each map entry ~50 bytes
                    size += msg.getMapValue().size() * 50;
                }
                // Add overhead for other fields
                if (msg.getSender() != null) size += msg.getSender().length();
                if (msg.getTopic() != null) size += msg.getTopic().length();
                return size;
            }

            private String formatSize(int bytes) {
                if (bytes < 1024) {
                    return bytes + "B";
                } else if (bytes < 1024 * 1024) {
                    return String.format("%.1fKB", bytes / 1024.0);
                } else {
                    return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
                }
            }

            private String formatRoundtrip(long ms) {
                if (ms < 1000) {
                    return ms + "ms";
                } else if (ms < 60000) {
                    return String.format("%.2fs", ms / 1000.0);
                } else {
                    return String.format("%.1fm", ms / 60000.0);
                }
            }

            private String truncate(String str, int maxLen) {
                if (str == null) return "";
                if (str.length() <= maxLen) return str;
                // Truncate with ellipsis
                return str.substring(0, Math.max(0, maxLen - 1)) + "…";
            }

            private String padRight(String str, int len) {
                if (str == null) str = "";
                if (str.length() >= len) return str;
                return str + " ".repeat(len - str.length());
            }
        });
    }
}

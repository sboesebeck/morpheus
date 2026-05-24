package de.caluga.morpheus.commands;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import de.caluga.morpheus.IRequiresMorphium;
import de.caluga.morpheus.Morpheus;
import de.caluga.morpheus.utils.TerminalUtils;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.changestream.ChangeStreamEvent;
import de.caluga.morphium.changestream.ChangeStreamListener;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;

/**
 * TOP-like message monitor with in-place updates and summary statistics
 */
public class MessageMonitor implements IRequiresMorphium {
    public final static String NAME = "monitor";
    public final static String DESCRIPTION = "Real-time messaging monitor with TOP-like view. Use --verbose for RTT debug info";

    // Message tracking data structure
    private static class MessageInfo {
        MorphiumId id;
        String sender;
        String senderHost;
        String recipient;
        String topic;
        int size;
        long timestamp;
        boolean isAnswer;
        int processedByCount = 0; // Track how many processors handled this
        String lockedBy = null; // Who locked this message
        Long lockedUntil = null; // Lock expiry timestamp
        String answeredBy = null; // Who sent the answer
        String answeredByHost = null; // Host of the answerer
        boolean isExclusive = false; // Whether message is exclusive
        Long rtt; // null until answered
        int updateCount = 1;
        boolean isTimedOut = false; // true if no answer after timeout threshold
        boolean isDeleteAfterProcessing = false;
        int deleteAfterProcessingTime = 0;
        boolean isV5 = false; // true if message has no 'topic' field (V5 format)
        boolean answerIsV5 = false; // true if answer message is V5 format
        boolean isDeleted = false; // true if message was deleted from collection

        MessageInfo(Msg msg, String topic) {
            this.id = msg.getMsgId();
            this.sender = msg.getSender();
            this.senderHost = msg.getSenderHost();
            this.recipient = msg.getRecipients() != null && !msg.getRecipients().isEmpty()
                             ? msg.getRecipients().get(0) : "";
            this.topic = topic; // Pass topic explicitly for V5/V6 compatibility
            this.size = calculateSize(msg);
            this.timestamp = msg.getTimestamp();
            this.isAnswer = msg.isAnswer();
            this.isExclusive = msg.isExclusive();
            this.processedByCount = msg.getProcessedBy() != null ? msg.getProcessedBy().size() : 0;
            this.isDeleteAfterProcessing = msg.isDeleteAfterProcessing();
            this.deleteAfterProcessingTime = msg.getDeleteAfterProcessingTime();

            // Detect V5 format: if msg.getTopic() returns null/empty, it's V5 (uses 'name' field)
            try {
                this.isV5 = (msg.getTopic() == null || msg.getTopic().isEmpty());
            } catch (Exception e) {
                // getTopic() might not exist in V5 - assume V5 if exception
                this.isV5 = true;
            }
        }

        void update(Msg msg) {
            // Update fields that can change
            if (msg.getProcessedBy() != null) {
                int newCount = msg.getProcessedBy().size();
                if (newCount > this.processedByCount) {
                    this.processedByCount = newCount;
                    this.updateCount++;
                }
            }
        }

        private int calculateSize(Msg msg) {
            int size = 0;
            if (msg.getMsg() != null) size += msg.getMsg().length();
            if (msg.getMapValue() != null) size += msg.getMapValue().size() * 50;
            if (msg.getSender() != null) size += msg.getSender().length();
            try {
                if (msg.getTopic() != null) size += msg.getTopic().length();
            } catch (Exception e) {
                // Ignore - V5 message without topic
            }
            return size;
        }
    }

    @Override
    public void execute(Morpheus morpheus, Map<String, String> args) throws Exception {
        boolean verbose = args.containsKey("--verbose");

        // Timeout threshold (configurable via --timeout parameter, default 2000ms)
        long timeoutThresholdMs = args.containsKey("--timeout")
                                      ? Long.parseLong(args.get("--timeout"))
                                      : 2000L;

        // Terminal size - will be updated on resize
        class TerminalState {
            volatile int width;
            volatile int height;
            volatile int maxMessages;
            volatile boolean needsRecalc = false;

            TerminalState(TerminalUtils.Size size) {
                this.width = size.getCol();
                this.height = size.getRow();
                this.maxMessages = Math.max(10, height - 7 - 2);
            }

            synchronized void resize(TerminalUtils.Size size) {
                this.width = size.getCol();
                this.height = size.getRow();
                this.maxMessages = Math.max(10, height - 7 - 2);
                this.needsRecalc = true;
            }

            synchronized boolean checkAndClearRecalc() {
                boolean result = needsRecalc;
                needsRecalc = false;
                return result;
            }
        }

        TerminalState termState = new TerminalState(TerminalUtils.getTerminalSize(verbose));

        // Flag to trigger redraw on resize
        final AtomicBoolean resizeTriggered = new AtomicBoolean(false);

        // Register signal handler for terminal resize (SIGWINCH) for immediate reaction
        try {
            sun.misc.Signal.handle(new sun.misc.Signal("WINCH"), signal -> {
                TerminalUtils.Size newSize = TerminalUtils.getTerminalSize(false);
                if (newSize.getCol() != termState.width || newSize.getRow() != termState.height) {
                    termState.resize(newSize);
                    resizeTriggered.set(true); // Trigger immediate redraw
                    if (verbose) {
                        System.err.println("DEBUG: Terminal resized to " + newSize.getCol() + "x" + newSize.getRow());
                    }
                }
            });
        } catch (Exception e) {
            if (verbose) {
                morpheus.pr("[warning]Could not register resize handler: " + e.getMessage() + "[r]");
            }
        }

        if (verbose) {
            morpheus.pr("[c3]Terminal: " + termState.width + "x" + termState.height + ", Buffer: " + termState.maxMessages + " messages[r]");
            morpheus.pr("[c3]Timeout threshold: " + timeoutThresholdMs + "ms[r]");
        }

        // Statistics counters
        AtomicLong totalMessages = new AtomicLong(0);
        AtomicLong totalAnswers = new AtomicLong(0);
        AtomicLong totalUpdates = new AtomicLong(0);
        AtomicLong totalTimeouts = new AtomicLong(0);
        AtomicLong startTime = new AtomicLong(System.currentTimeMillis());

        // Topic-based RTT statistics
        // Map: topic -> [sum of RTTs, count of answers]
        ConcurrentHashMap<String, long[]> topicRttStats = new ConcurrentHashMap<>();

        // Host/Sender activity statistics
        // Map: host -> message count
        ConcurrentHashMap<String, AtomicInteger> hostStats = new ConcurrentHashMap<>();
        // Map: sender -> message count
        ConcurrentHashMap<String, AtomicInteger> senderStats = new ConcurrentHashMap<>();

        // Message buffer - keeps last N messages in order
        // Using LinkedHashMap for insertion order
        Map<MorphiumId, MessageInfo> messageBuffer = new LinkedHashMap<MorphiumId, MessageInfo>(termState.maxMessages + 1, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<MorphiumId, MessageInfo> eldest) {
                return size() > termState.maxMessages;
            }
        };

        // Track original message timestamps and topics for RTT calculation
        ConcurrentHashMap<MorphiumId, Long> messageTimestamps = new ConcurrentHashMap<>();
        ConcurrentHashMap<MorphiumId, String> messageTopics = new ConcurrentHashMap<>();

        // Column visibility toggles (like TOP)
        class ColumnVisibility {
            volatile boolean showTimestamp = true;
            volatile boolean showSender = true;
            volatile boolean showSenderHost = true;
            volatile boolean showRecipient = false; // Hidden by default
            volatile boolean showTopic = true;
            volatile boolean showSize = true;
            volatile boolean showProcessed = true;
            volatile boolean showExclusive = true; // Show exclusive flag
            volatile boolean showAnswer = true;
            volatile boolean showAnsweredBy = true; // Show who answered
            volatile boolean showAnsweredByHost = false; // Hidden by default
            volatile boolean showRtt = true;
        }

        ColumnVisibility colVis = new ColumnVisibility();

        // Column widths calculation (will be recalculated on resize)
        class ColumnWidths {
            int timestamp, sender, senderHost, recipient, topic, processed, answeredBy, answeredByHost;

            void calculate(int termWidth, ColumnVisibility vis) {
                // Fixed columns with exact widths
                int fixedTotal = 0;
                fixedTotal += 5;  // "#" column always shown
                if (vis.showTimestamp) fixedTotal += 8;   // "Time" column
                if (vis.showSize) fixedTotal += 8;        // "Size" column
                if (vis.showExclusive) fixedTotal += 4;   // "Excl" column (just "X" or empty)
                if (vis.showAnswer) fixedTotal += 6;      // "Ans" column (3 chars + padding)
                if (vis.showRtt) fixedTotal += 11;        // "RTT" column

                // Count separators: " │ " = 3 chars each
                int numCols = 1; // # always present
                if (vis.showTimestamp) numCols++;
                if (vis.showSender) numCols++;
                if (vis.showSenderHost) numCols++;
                if (vis.showRecipient) numCols++;
                if (vis.showTopic) numCols++;
                if (vis.showSize) numCols++;
                if (vis.showProcessed) numCols++;
                if (vis.showExclusive) numCols++;
                if (vis.showAnswer) numCols++;
                if (vis.showAnsweredBy) numCols++;
                if (vis.showAnsweredByHost) numCols++;
                if (vis.showRtt) numCols++;

                int separatorTotal = (numCols - 1) * 3;

                // Calculate available width for variable columns
                int available = termWidth - fixedTotal - separatorTotal - 2; // -2 for safety

                // Count variable columns
                int varCount = 0;
                if (vis.showSender) varCount++;
                if (vis.showSenderHost) varCount++;
                if (vis.showRecipient) varCount++;
                if (vis.showTopic) varCount++;
                if (vis.showProcessed) varCount++;
                if (vis.showAnsweredBy) varCount++;
                if (vis.showAnsweredByHost) varCount++;

                // Distribute width
                if (varCount > 0 && available > 0) {
                    int widthPerVar = available / varCount;
                    sender = vis.showSender ? Math.max(8, widthPerVar) : 0;
                    senderHost = vis.showSenderHost ? Math.max(8, widthPerVar) : 0;
                    recipient = vis.showRecipient ? Math.max(8, widthPerVar) : 0;
                    topic = vis.showTopic ? Math.max(10, widthPerVar) : 0;
                    processed = vis.showProcessed ? Math.max(8, widthPerVar) : 0;
                    answeredBy = vis.showAnsweredBy ? Math.max(8, widthPerVar) : 0;
                    answeredByHost = vis.showAnsweredByHost ? Math.max(8, widthPerVar) : 0;
                } else {
                    // Fallback to minimal widths
                    sender = vis.showSender ? 10 : 0;
                    senderHost = vis.showSenderHost ? 10 : 0;
                    recipient = vis.showRecipient ? 10 : 0;
                    topic = vis.showTopic ? 12 : 0;
                    processed = vis.showProcessed ? 8 : 0;
                    answeredBy = vis.showAnsweredBy ? 10 : 0;
                    answeredByHost = vis.showAnsweredByHost ? 10 : 0;
                }

                timestamp = vis.showTimestamp ? 8 : 0;
            }
        }

        ColumnWidths colWidths = new ColumnWidths();
        colWidths.calculate(termState.width, colVis);

        Morphium m = morpheus.getMorphium();

        // Initial screen clear and header
        System.out.print("\033[2J\033[H"); // Clear screen and move cursor to home
        System.out.flush();

        // TODO: Add keyboard listener for interactive column toggling later
        // For now, use fixed column visibility to avoid terminal issues
        AtomicBoolean keyboardNeedsRedraw = new AtomicBoolean(false);

        // Shared redraw state
        final AtomicBoolean forceRedraw = new AtomicBoolean(true); // Start with true for initial draw
        final long REDRAW_INTERVAL_MS = 500; // Redraw every 500ms max

        // Periodic refresh timer to update timeouts even when no messages arrive
        java.util.Timer refreshTimer = new java.util.Timer("MonitorRefresh", true);
        final long REFRESH_INTERVAL_MS = 1000; // Refresh every 1 second

        // Schedule periodic check for resize - just detection, the watchDb will handle redraws
        refreshTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                // Check for terminal size changes (polling fallback if SIGWINCH doesn't work)
                TerminalUtils.Size currentSize = TerminalUtils.getTerminalSize(false);
                if (currentSize.getCol() != termState.width || currentSize.getRow() != termState.height) {
                    termState.resize(currentSize);
                    resizeTriggered.set(true);
                    if (verbose) {
                        System.err.println("DEBUG: Polling - Terminal resized to " + currentSize.getCol() + "x" + currentSize.getRow());
                    }
                }
                // Set flag to trigger redraw in watchDb
                forceRedraw.set(true);
            }
        }, 0, REFRESH_INTERVAL_MS); // Start immediately with delay 0

        // Helper method to check lock status for a message

        m.watchDb(true, new ChangeStreamListener() {

            // Check lock status for a message ID
            private void updateLockStatus(MessageInfo info) {
                try {
                    final String lockCollectionName = morpheus.getMessaging().getLockCollectionName(info.topic);
                    // Query lock collection for this message ID
                    var lockQuery = m.createQueryFor(Map.class, lockCollectionName)
                                    .f("_id").eq(info.id);
                    Map<String, Object> lockDoc = lockQuery.get();

                    if (lockDoc != null) {
                        info.lockedBy = (String) lockDoc.get("locked_by");
                        Object lockedUntilObj = lockDoc.get("locked_until");
                        if (lockedUntilObj instanceof Number) {
                            info.lockedUntil = ((Number) lockedUntilObj).longValue();
                        }
                    } else {
                        info.lockedBy = null;
                        info.lockedUntil = null;
                    }
                } catch (Exception e) {
                    // Lock collection might not exist or query failed - ignore
                    if (verbose) {
                        System.err.println("Lock query failed: " + e.getMessage());
                    }
                }
            }

            @Override
            public boolean incomingData(ChangeStreamEvent evt) {
                try {
                    if (verbose) System.err.println("[DEBUG] Event type: " + evt.getOperationType());

                    // Handle delete events - mark message as deleted
                    if (evt.getOperationType().equals("delete")) {
                        if (verbose) System.err.println("[DEBUG] Processing delete event");
                        Object docIdObj = ((Map) evt.getDocumentKey()).get("_id");
                        if (docIdObj instanceof MorphiumId) {
                            MorphiumId docId = (MorphiumId) docIdObj;
                            synchronized (messageBuffer) {
                                MessageInfo existing = messageBuffer.get(docId);
                                if (existing != null) {
                                    existing.isDeleted = true;
                                    existing.updateCount++;
                                    if (verbose) System.err.println("[DEBUG] Marked message as deleted: " + docId);
                                    redrawScreen();
                                }
                            }
                        }
                        return true; // Continue listening
                    }

                    // Try to get fullDocument first
                    Map<String, Object> doc = evt.getFullDocument();

                    // If fullDocument is null, try to fetch by ID
                    if (doc == null) {
                        Object docIdObj = evt.getDocumentKey();
                        if (docIdObj instanceof MorphiumId) {
                            MorphiumId docId = (MorphiumId) docIdObj;
                            Msg msg = morpheus.getMorphium().findById(Msg.class, docId);
                            if (msg != null) {
                                doc = morpheus.getMorphium().getMapper().serialize(msg);
                            }
                        }
                    }

                    // If we still don't have a document, skip
                    if (doc == null) return true;

                    // Check if this looks like a message document
                    if (!doc.containsKey("name") && !doc.containsKey("topic")) {
                        return true;
                    }

                    // Deserialize message
                    if (verbose) System.err.println("[DEBUG] Deserializing message...");
                    Msg msg = morpheus.getMorphium().getMapper().deserialize(Msg.class, doc);
                    if (msg == null || msg.getMsgId() == null) {
                        if (verbose) System.err.println("[DEBUG] Message is null or has no ID");
                        return true;
                    }
                    if (verbose) System.err.println("[DEBUG] Message ID: " + msg.getMsgId());

                    // V5/V6 compatibility: Extract topic/name from document
                    // V6 uses 'topic', V5 used 'name'
                    String messageTopic = (String) doc.getOrDefault("topic", doc.get("name"));

                    boolean needsRedraw = false;


                    synchronized (messageBuffer) {
                        if (evt.getOperationType().equals("insert")) {
                            // New message
                            if (verbose) System.err.println("[DEBUG] Processing INSERT event");
                            totalMessages.incrementAndGet();

                            // Check if this is an answer to a message in our buffer
                            if (msg.isAnswer() && msg.getInAnswerTo() != null) {
                                if (verbose) System.err.println("[DEBUG] Processing ANSWER to: " + msg.getInAnswerTo());
                                totalAnswers.incrementAndGet();

                                // Find the original message in buffer
                                MessageInfo originalMsg = messageBuffer.get(msg.getInAnswerTo());
                                Long originalTime = messageTimestamps.get(msg.getInAnswerTo());
                                String originalTopic = messageTopics.get(msg.getInAnswerTo());

                                if (originalTime != null) {
                                    long roundtripMs = msg.getTimestamp() - originalTime;
                                    if (roundtripMs >= 0) {
                                        // Update the ORIGINAL message with RTT and answerer info
                                        if (originalMsg != null) {
                                            if (verbose) System.err.println("[DEBUG] Updating original message with RTT: " + roundtripMs);
                                            originalMsg.rtt = roundtripMs;
                                            originalMsg.isTimedOut = false; // Clear timeout if answered
                                            originalMsg.answeredBy = msg.getSender();
                                            originalMsg.answeredByHost = msg.getSenderHost();
                                            // Detect if answer is V5 format
                                            try {
                                                if (verbose) System.err.println("[DEBUG] Detecting answer version...");
                                                originalMsg.answerIsV5 = (msg.getTopic() == null || msg.getTopic().isEmpty());
                                                if (verbose) System.err.println("[DEBUG] Answer is V5: " + originalMsg.answerIsV5);
                                            } catch (Exception e) {
                                                if (verbose) System.err.println("[DEBUG] getTopic() failed, assuming V5: " + e.getMessage());
                                                originalMsg.answerIsV5 = true; // Assume V5 if getTopic() fails
                                            }
                                            needsRedraw = true; // Redraw to show RTT on original message
                                        }

                                        // Update topic RTT statistics
                                        if (originalTopic != null && !originalTopic.isEmpty()) {
                                            topicRttStats.compute(originalTopic, (k, v) -> {
                                                if (v == null) {
                                                    return new long[] {roundtripMs, 1}; // [sum, count]
                                                } else {
                                                    v[0] += roundtripMs; // add to sum
                                                    v[1]++; // increment count
                                                    return v;
                                                }
                                            });
                                        }
                                    }
                                    messageTimestamps.remove(msg.getInAnswerTo());
                                    messageTopics.remove(msg.getInAnswerTo());
                                }

                                // Don't add the answer to buffer - we only track requests
                                // This saves space and focuses on what matters: request status

                            } else if (!msg.isAnswer()) {
                                // This is a new request message
                                MessageInfo info = new MessageInfo(msg, messageTopic);

                                // Track message for RTT calculation
                                messageTimestamps.put(msg.getMsgId(), msg.getTimestamp());
                                if (messageTopic != null && !messageTopic.isEmpty()) {
                                    messageTopics.put(msg.getMsgId(), messageTopic);
                                }

                                // Update host/sender statistics
                                if (msg.getSenderHost() != null && !msg.getSenderHost().isEmpty()) {
                                    hostStats.computeIfAbsent(msg.getSenderHost(), k -> new AtomicInteger(0)).incrementAndGet();
                                }
                                if (msg.getSender() != null && !msg.getSender().isEmpty()) {
                                    senderStats.computeIfAbsent(msg.getSender(), k -> new AtomicInteger(0)).incrementAndGet();
                                }

                                messageBuffer.put(msg.getMsgId(), info);

                                // Check lock status for this message
                                updateLockStatus(info);

                                needsRedraw = true;
                            }

                        } else if (evt.getOperationType().equals("update") || evt.getOperationType().equals("replace")) {
                            // Message updated (e.g., processedBy added)
                            MessageInfo existing = messageBuffer.get(msg.getMsgId());
                            if (existing != null) {
                                existing.update(msg);

                                // Update lock status
                                updateLockStatus(existing);

                                totalUpdates.incrementAndGet();
                                needsRedraw = true;
                            }
                        }
                    }

                    // Check if terminal was resized
                    if (resizeTriggered.getAndSet(false)) {
                        needsRedraw = true; // Force immediate redraw after resize
                    }

                    // Check if keyboard triggered column change
                    if (keyboardNeedsRedraw.getAndSet(false)) {
                        needsRedraw = true; // Force immediate redraw after column toggle
                    }

                    // Check if periodic timer requested a redraw
                    if (forceRedraw.getAndSet(false)) {
                        needsRedraw = true;
                    }

                    // Redraw if needed
                    if (needsRedraw) {
                        if (verbose) System.err.println("[DEBUG] Calling redrawScreen...");
                        redrawScreen();
                        if (verbose) System.err.println("[DEBUG] redrawScreen completed");
                    }

                } catch (Exception e) {
                    if (verbose) {
                        morpheus.pr("[error]Error processing change stream event: " + e.getMessage() + "[r]");
                        e.printStackTrace(System.err);
                    }
                }
                if (verbose) System.err.println("[DEBUG] incomingData returning true");
                return true;
            }

            private void redrawScreen() {
                StringBuilder sb = new StringBuilder();

                // Always recalculate column widths based on current terminal size
                // This ensures we adapt to any size changes
                colWidths.calculate(termState.width, colVis);

                // Clear flag if set
                termState.checkAndClearRecalc();

                // Clear screen and move to home
                sb.append("\033[2J\033[H"); // Clear screen + move to home

                // Use current terminal width
                int currentWidth = termState.width;
                int currentHeight = termState.height;

                // Header banner
                String banner = "MORPHEUS MESSAGE MONITOR";
                int padding = (currentWidth - banner.length()) / 2;
                sb.append("\033[1;34m").append(" ".repeat(Math.max(0, padding))).append(banner)
                  .append(" ".repeat(Math.max(0, currentWidth - banner.length() - padding))).append("\033[0m\n");

                // Statistics
                long uptime = (System.currentTimeMillis() - startTime.get()) / 1000;
                long msgs = totalMessages.get();
                long answers = totalAnswers.get();
                long updates = totalUpdates.get();
                long timeouts = totalTimeouts.get();
                double msgPerSec = uptime > 0 ? msgs / (double)uptime : 0;

                sb.append(String.format("\033[33mUptime: %ds | Messages: %d (%.1f/s) | Answers: %d | Updates: %d | \033[91mTimeouts: %d\033[33m | Buffer: %d/%d\033[0m\n",
                                        uptime, msgs, msgPerSec, answers, updates, timeouts, messageBuffer.size(), termState.maxMessages));

                // Topic RTT statistics (top 5 slowest topics)
                if (!topicRttStats.isEmpty()) {
                    sb.append("\033[35m"); // Magenta for topic stats
                    sb.append("Avg RTT by Topic: ");

                    // Sort topics by average RTT (descending)
                    topicRttStats.entrySet().stream()
                    .sorted((a, b) -> {
                        long avgA = a.getValue()[0] / a.getValue()[1];
                        long avgB = b.getValue()[0] / b.getValue()[1];
                        return Long.compare(avgB, avgA); // Descending
                    })
                    .limit(5) // Show top 5 slowest
                    .forEach(entry -> {
                        String topic = entry.getKey();
                        long[] stats = entry.getValue();
                        long avgRtt = stats[0] / stats[1]; // sum / count
                        long count = stats[1];
                        sb.append(String.format("%s:%s(n=%d) ",
                                                truncate(topic, 15),
                                                formatRoundtrip(avgRtt),
                                                count));
                    });
                    sb.append("\033[0m\n");
                }

                // Host/Sender activity statistics
                if (!hostStats.isEmpty() || !senderStats.isEmpty()) {
                    sb.append("\033[36m"); // Cyan for activity stats

                    // Top 5 most active hosts
                    if (!hostStats.isEmpty()) {
                        sb.append("Active Hosts: ");
                        hostStats.entrySet().stream()
                                 .sorted((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get())) // Descending by count
                                 .limit(5)
                        .forEach(entry -> {
                            sb.append(String.format("%s(%d) ",
                                                    truncate(entry.getKey(), 20),
                                                    entry.getValue().get()));
                        });
                    }

                    // Top 5 most active senders
                    if (!senderStats.isEmpty()) {
                        if (!hostStats.isEmpty()) sb.append(" | ");
                        sb.append("Active Senders: ");
                        senderStats.entrySet().stream()
                                   .sorted((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get())) // Descending by count
                                   .limit(5)
                        .forEach(entry -> {
                            sb.append(String.format("%s(%d) ",
                                                    truncate(entry.getKey(), 20),
                                                    entry.getValue().get()));
                        });
                    }

                    sb.append("\033[0m\n");
                }

                // Column headers (dynamic based on visibility)
                String separator = "─".repeat(currentWidth - 1);
                sb.append("\033[36m").append(separator).append("\033[0m\n");
                sb.append("\033[1m");
                sb.append(String.format("%-5s", "#"));
                if (colVis.showTimestamp) sb.append(" │ ").append(String.format("%-8s", "Time"));
                if (colVis.showSender) sb.append(" │ ").append(String.format("%-" + colWidths.sender + "s", "Sender"));
                if (colVis.showSenderHost) sb.append(" │ ").append(String.format("%-" + colWidths.senderHost + "s", "Host"));
                if (colVis.showRecipient) sb.append(" │ ").append(String.format("%-" + colWidths.recipient + "s", "Recipient"));
                if (colVis.showTopic) sb.append(" │ ").append(String.format("%-" + colWidths.topic + "s", "Topic"));
                if (colVis.showSize) sb.append(" │ ").append(String.format("%-8s", "Size"));
                if (colVis.showProcessed) sb.append(" │ ").append(String.format("%-" + colWidths.processed + "s", "Processed"));
                if (colVis.showExclusive) sb.append(" │ ").append(String.format("%-4s", "Excl"));
                if (colVis.showAnswer) sb.append(" │ ").append(String.format("%-6s", "Ans"));
                if (colVis.showAnsweredBy) sb.append(" │ ").append(String.format("%-" + colWidths.answeredBy + "s", "AnswerBy"));
                if (colVis.showAnsweredByHost) sb.append(" │ ").append(String.format("%-" + colWidths.answeredByHost + "s", "AnsHost"));
                if (colVis.showRtt) sb.append(" │ ").append(String.format("%-11s", "RTT"));
                sb.append("\033[0m\n");
                sb.append("\033[36m").append(separator).append("\033[0m\n");

                // Message rows (newest first)
                synchronized (messageBuffer) {
                    int rowNum = 0;
                    long currentTime = System.currentTimeMillis();

                    // Collect messages into list and reverse to show newest first
                    java.util.List<MessageInfo> messages = new java.util.ArrayList<>(messageBuffer.values());
                    java.util.Collections.reverse(messages);

                    for (MessageInfo info : messages) {
                        rowNum++;

                        // Check for timeout (only for non-answer messages without RTT)
                        if (!info.isAnswer && info.rtt == null) {
                            long age = currentTime - info.timestamp;
                            if (age > timeoutThresholdMs && !info.isTimedOut) {
                                info.isTimedOut = true;
                                totalTimeouts.incrementAndGet();
                            }
                        }

                        // Alternating row colors - use white and bright white for better readability
                        String rowColor = (rowNum % 2 == 0) ? "\033[97m" : "\033[37m"; // Bright white / white

                        // // Highlight updated messages in yellow
                        // if (info.updateCount > 1) {
                        //     rowColor = "\033[93m"; // Bright yellow for updated
                        // }

                        // Highlight timed-out messages in RED (overrides others)
                        if (info.isTimedOut) {
                            rowColor = "\033[91m"; // Bright red for timeout
                        }

                        // Prepare display values
                        boolean hasAnswer = info.rtt != null;
                        String answerDisplay = hasAnswer ? "YES" : "";
                        String rttStr = info.rtt != null ? formatRoundtrip(info.rtt) : "";
                        String sizeStr = formatSize(info.size);
                        String timeStr = formatRelativeTime(currentTime - info.timestamp);
                        // Show processed count, lock status, deleted status, or note if answered but not processed
                        String processedStr = "";
                        if (info.isDeleted) {
                            processedStr = "DELETED";
                        } else if (info.processedByCount > 0) {
                            processedStr = info.processedByCount + " proc";
                        } else if (info.lockedBy != null) {
                            // Message is locked - show who locked it
                            boolean lockExpired = info.lockedUntil != null && info.lockedUntil < currentTime;
                            if (lockExpired) {
                                processedStr = "lock-exp:" + truncate(info.lockedBy, 8);
                            } else {
                                processedStr = "locked:" + truncate(info.lockedBy, 8);
                            }
                        } else if (hasAnswer) {
                            // Message was answered but processed_by is empty and not locked
                            if (info.isDeleteAfterProcessing && info.deleteAfterProcessingTime == 0) {
                                processedStr = "deleted after processing";
                            } else if (info.isDeleteAfterProcessing && info.deleteAfterProcessingTime != 0) {
                                processedStr = "delete (after " + info.deleteAfterProcessingTime + "ms)";
                            } else {
                                processedStr = "ans-only";
                            }
                        }

                        // Build row dynamically based on column visibility
                        sb.append(rowColor);
                        sb.append(String.format("%-5d", rowNum));

                        if (colVis.showTimestamp) {
                            sb.append(" │ ").append(String.format("%-8s", timeStr));
                        }
                        if (colVis.showSender) {
                            // Add V5/V6 marker: V5 = yellow, V6 = green
                            String versionColor = info.isV5 ? "\033[93m" : "\033[32m";
                            String versionTag = info.isV5 ? "[V5]" : "[V6]";
                            String senderText = truncate(info.sender, colWidths.sender - 4); // -4 for [Vx]
                            String senderPad = " ".repeat(Math.max(0, colWidths.sender - senderText.length() - 4));
                            sb.append(" │ ").append(rowColor).append(senderText).append(versionColor).append(versionTag).append(rowColor).append(senderPad);
                        }
                        if (colVis.showSenderHost) {
                            String versionColor = info.isV5 ? "\033[93m" : "\033[32m";
                            String versionTag = info.isV5 ? "[V5]" : "[V6]";
                            String hostText = truncate(info.senderHost != null ? info.senderHost : "", colWidths.senderHost - 4);
                            String hostPad = " ".repeat(Math.max(0, colWidths.senderHost - hostText.length() - 4));
                            sb.append(" │ ").append(rowColor).append(hostText).append(versionColor).append(versionTag).append(rowColor).append(hostPad);
                        }
                        if (colVis.showRecipient) {
                            sb.append(" │ ").append(rowColor).append(String.format("%-" + colWidths.recipient + "s", truncate(info.recipient, colWidths.recipient)));
                        }
                        if (colVis.showTopic) {
                            String topicStr = info.topic;
                            if (info.isDeleted) {
                                // Add [DEL] marker for deleted messages
                                topicStr = "[DEL] " + topicStr;
                            }
                            sb.append(" │ ").append(rowColor).append(String.format("%-" + colWidths.topic + "s", truncate(topicStr, colWidths.topic)));
                        }
                        if (colVis.showSize) {
                            sb.append(" │ ").append(rowColor).append(String.format("%-8s", sizeStr));
                        }
                        if (colVis.showProcessed) {
                            sb.append(" │ ").append(rowColor).append(String.format("%-" + colWidths.processed + "s", processedStr));
                        }

                        if (colVis.showExclusive) {
                            String exclDisplay = info.isExclusive ? "X" : "";
                            sb.append(" │ ").append(rowColor).append(String.format("%-4s", exclDisplay));
                        }

                        if (colVis.showAnswer) {
                            sb.append(" │ ");
                            if (hasAnswer) {
                                sb.append("\033[32m"); // Green for YES
                                sb.append(String.format("%-3s", answerDisplay));
                                sb.append("\033[0m").append(rowColor); // Reset and back to row color
                            } else {
                                sb.append(rowColor).append(String.format("%-3s", answerDisplay));
                            }
                        }

                        if (colVis.showAnsweredBy) {
                            if (info.answeredBy != null) {
                                // Add V5/V6 marker for answer: V5 = yellow, V6 = green
                                String versionMarker = info.answerIsV5 ? "\033[93m[V5]\033[0m" : "\033[32m[V6]\033[0m";
                                String answeredByText = truncate(info.answeredBy, colWidths.answeredBy - 4);
                                String answeredByPad = " ".repeat(Math.max(0, colWidths.answeredBy - answeredByText.length() - 4));
                                sb.append(" │ ").append(rowColor).append(answeredByText).append(versionMarker).append(rowColor).append(answeredByPad);
                            } else {
                                sb.append(" │ ").append(rowColor).append(" ".repeat(colWidths.answeredBy));
                            }
                        }

                        if (colVis.showAnsweredByHost) {
                            if (info.answeredByHost != null) {
                                String versionMarker = info.answerIsV5 ? "\033[93m[V5]\033[0m" : "\033[32m[V6]\033[0m";
                                String answeredByHostText = truncate(info.answeredByHost, colWidths.answeredByHost - 4);
                                String answeredByHostPad = " ".repeat(Math.max(0, colWidths.answeredByHost - answeredByHostText.length() - 4));
                                sb.append(" │ ").append(rowColor).append(answeredByHostText).append(versionMarker).append(rowColor).append(answeredByHostPad);
                            } else {
                                sb.append(" │ ").append(rowColor).append(" ".repeat(colWidths.answeredByHost));
                            }
                        }

                        if (colVis.showRtt) {
                            sb.append(" │ ").append(rowColor).append(String.format("%-11s", rttStr));
                        }

                        sb.append("\033[0m"); // Reset color
                        sb.append("\n");
                    }

                    // Fill remaining lines with blanks to avoid ghosting
                    int currentMaxMessages = termState.maxMessages;
                    for (int i = messageBuffer.size(); i < currentMaxMessages; i++) {
                        sb.append(" ".repeat(currentWidth - 1)).append("\n");
                    }
                }

                // Footer
                sb.append("\033[36m").append(separator).append("\033[0m\n");
                sb.append("\033[90mPress Ctrl+C to exit\033[0m");

                // Clear to end of screen
                sb.append("\033[J");

                System.out.print(sb.toString());
                System.out.flush();
            }

            private String formatRelativeTime(long ageMs) {
                long seconds = ageMs / 1000;
                if (seconds < 60) return seconds + "s";
                else if (seconds < 3600) return (seconds / 60) + "m";
                else return (seconds / 3600) + "h";
            }

            private String formatSize(int bytes) {
                if (bytes < 1024) return bytes + "B";
                else if (bytes < 1024 * 1024) return String.format("%.1fK", bytes / 1024.0);
                else return String.format("%.1fM", bytes / (1024.0 * 1024.0));
            }

            private String formatRoundtrip(long ms) {
                if (ms < 1000) return ms + "ms";
                else if (ms < 60000) return String.format("%.2fs", ms / 1000.0);
                else return String.format("%.1fm", ms / 60000.0);
            }

            private String truncate(String str, int maxLen) {
                if (str == null || str.isEmpty()) return "";
                if (str.length() <= maxLen) return str;
                return str.substring(0, Math.max(0, maxLen - 1)) + "…";
            }
        });
    }
}

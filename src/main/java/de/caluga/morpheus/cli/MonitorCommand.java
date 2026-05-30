package de.caluga.morpheus.cli;

import de.caluga.morpheus.core.MessageFeed;
import de.caluga.morpheus.core.MessageInfo;
import de.caluga.morpheus.core.MessageStats;
import de.caluga.morpheus.core.MessageTracker;
import de.caluga.morpheus.utils.TerminalUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

@Command(name = "monitor", description = "Real-time messaging monitor (TOP-like).",
         mixinStandardHelpOptions = true)
public class MonitorCommand implements Callable<Integer> {

    @ParentCommand RootCommand parent;

    @Option(names = {"--timeout"}, defaultValue = "2000",
            description = "Answer timeout threshold in ms (default: ${DEFAULT-VALUE}).")
    long timeoutThresholdMs;

    // ── instance fields so redrawScreen() can access them ──────────────────────

    private TerminalState termState;
    private MessageTracker tracker;
    private ColumnVisibility colVis;
    private ColumnWidths colWidths;
    private boolean verbose;

    // ── inner classes ───────────────────────────────────────────────────────────

    /** Tracks current terminal dimensions and max message buffer size. */
    private static class TerminalState {
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

    /** Which columns are currently shown. */
    private static class ColumnVisibility {
        volatile boolean showTimestamp = true;
        volatile boolean showSender = true;
        volatile boolean showSenderHost = true;
        volatile boolean showRecipient = false;
        volatile boolean showTopic = true;
        volatile boolean showSize = true;
        volatile boolean showProcessed = true;
        volatile boolean showExclusive = true;
        volatile boolean showAnswer = true;
        volatile boolean showAnsweredBy = true;
        volatile boolean showAnsweredByHost = false;
        volatile boolean showRtt = true;
    }

    /** Calculated pixel widths for variable-width columns. */
    private static class ColumnWidths {
        int timestamp, sender, senderHost, recipient, topic, processed, answeredBy, answeredByHost;

        void calculate(int termWidth, ColumnVisibility vis) {
            int fixedTotal = 0;
            fixedTotal += 5;  // "#" always shown
            if (vis.showTimestamp) fixedTotal += 8;
            if (vis.showSize) fixedTotal += 8;
            if (vis.showExclusive) fixedTotal += 4;
            if (vis.showAnswer) fixedTotal += 6;
            if (vis.showRtt) fixedTotal += 11;

            int numCols = 1;
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
            int available = termWidth - fixedTotal - separatorTotal - 2;

            int varCount = 0;
            if (vis.showSender) varCount++;
            if (vis.showSenderHost) varCount++;
            if (vis.showRecipient) varCount++;
            if (vis.showTopic) varCount++;
            if (vis.showProcessed) varCount++;
            if (vis.showAnsweredBy) varCount++;
            if (vis.showAnsweredByHost) varCount++;

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

    // ── entry point ─────────────────────────────────────────────────────────────

    @Override
    public Integer call() throws Exception {
        MorpheusContext ctx = parent.context();
        ctx.connect();
        verbose = ctx.getConfig().isVerbose();

        // 1. Terminal state (determines initial buffer size)
        termState = new TerminalState(TerminalUtils.getTerminalSize(verbose));

        // 2. Tracker uses termState.maxMessages as the initial buffer cap
        tracker = new MessageTracker(termState.maxMessages);

        // 3. SIGWINCH handler — resize termState AND notify tracker
        final AtomicBoolean resizeTriggered = new AtomicBoolean(false);
        try {
            sun.misc.Signal.handle(new sun.misc.Signal("WINCH"), signal -> {
                TerminalUtils.Size newSize = TerminalUtils.getTerminalSize(false);
                if (newSize.getCol() != termState.width || newSize.getRow() != termState.height) {
                    termState.resize(newSize);
                    tracker.setMaxMessages(termState.maxMessages);
                    resizeTriggered.set(true);
                    if (verbose) {
                        System.err.println("DEBUG: Terminal resized to "
                                + newSize.getCol() + "x" + newSize.getRow());
                    }
                }
            });
        } catch (Exception e) {
            if (verbose) {
                System.err.println("Could not register resize handler: " + e.getMessage());
            }
        }

        if (verbose) {
            System.err.println("Terminal: " + termState.width + "x" + termState.height
                    + ", Buffer: " + termState.maxMessages + " messages");
            System.err.println("Timeout threshold: " + timeoutThresholdMs + "ms");
        }

        // 4. Column visibility and widths
        colVis = new ColumnVisibility();
        colWidths = new ColumnWidths();
        colWidths.calculate(termState.width, colVis);

        // 5. Initial screen clear
        System.out.print("\033[2J\033[H");
        System.out.flush();

        // 6. Refresh timer: polls resize AND calls markTimeouts + redraws every 1s
        java.util.Timer refreshTimer = new java.util.Timer("MonitorRefresh", true);
        refreshTimer.scheduleAtFixedRate(new java.util.TimerTask() {
            @Override
            public void run() {
                // Polling fallback for resize when SIGWINCH is unavailable
                TerminalUtils.Size currentSize = TerminalUtils.getTerminalSize(false);
                if (currentSize.getCol() != termState.width || currentSize.getRow() != termState.height) {
                    termState.resize(currentSize);
                    tracker.setMaxMessages(termState.maxMessages);
                    resizeTriggered.set(true);
                    if (verbose) {
                        System.err.println("DEBUG: Polling - Terminal resized to "
                                + currentSize.getCol() + "x" + currentSize.getRow());
                    }
                }
                // Mark timeouts and redraw even without incoming traffic
                tracker.markTimeouts(System.currentTimeMillis(), timeoutThresholdMs);
                redrawScreen();
            }
        }, 0, 1000L);

        // 7. Feed: blocks until DB disconnect
        MessageFeed feed = new MessageFeed(ctx.getMorphium(), ctx.getMessaging(),
                tracker, this::redrawScreen, verbose);
        feed.watch();

        refreshTimer.cancel();
        return 0;
    }

    // ── display ─────────────────────────────────────────────────────────────────

    private void redrawScreen() {
        StringBuilder sb = new StringBuilder();

        colWidths.calculate(termState.width, colVis);
        termState.checkAndClearRecalc();

        sb.append("\033[2J\033[H");

        int currentWidth = termState.width;
        int currentHeight = termState.height;

        // Header banner
        String banner = "MORPHEUS MESSAGE MONITOR";
        int padding = (currentWidth - banner.length()) / 2;
        sb.append("\033[1;34m")
          .append(" ".repeat(Math.max(0, padding)))
          .append(banner)
          .append(" ".repeat(Math.max(0, currentWidth - banner.length() - padding)))
          .append("\033[0m\n");

        // Statistics row
        MessageStats stats = tracker.getStats();
        long uptime = (System.currentTimeMillis() - stats.getStartTime()) / 1000;
        long msgs = stats.getTotalMessages();
        long answers = stats.getTotalAnswers();
        long updates = stats.getTotalUpdates();
        long timeouts = stats.getTotalTimeouts();
        double msgPerSec = uptime > 0 ? msgs / (double) uptime : 0;

        sb.append(String.format(
                "\033[33mUptime: %ds | Messages: %d (%.1f/s) | Answers: %d | Updates: %d"
                + " | \033[91mTimeouts: %d\033[33m | Buffer: %d/%d\033[0m\n",
                uptime, msgs, msgPerSec, answers, updates, timeouts,
                tracker.getBufferSize(), termState.maxMessages));

        // Topic RTT stats (top 5 slowest)
        var slowTopics = stats.getSlowestTopics(5);
        if (!slowTopics.isEmpty()) {
            sb.append("\033[35m");
            sb.append("Avg RTT by Topic: ");
            for (var t : slowTopics) {
                sb.append(String.format("%s:%s(n=%d) ",
                        truncate(t.topic(), 15),
                        formatRoundtrip(t.avgRtt()),
                        t.count()));
            }
            sb.append("\033[0m\n");
        }

        // Host / sender activity
        var topHosts = stats.getTopHosts(5);
        var topSenders = stats.getTopSenders(5);
        if (!topHosts.isEmpty() || !topSenders.isEmpty()) {
            sb.append("\033[36m");
            if (!topHosts.isEmpty()) {
                sb.append("Active Hosts: ");
                for (var e : topHosts) {
                    sb.append(String.format("%s(%d) ", truncate(e.name(), 20), e.count()));
                }
            }
            if (!topSenders.isEmpty()) {
                if (!topHosts.isEmpty()) sb.append(" | ");
                sb.append("Active Senders: ");
                for (var e : topSenders) {
                    sb.append(String.format("%s(%d) ", truncate(e.name(), 20), e.count()));
                }
            }
            sb.append("\033[0m\n");
        }

        // Column header row
        String separator = "─".repeat(currentWidth - 1);
        sb.append("\033[36m").append(separator).append("\033[0m\n");
        sb.append("\033[1m");
        sb.append(String.format("%-5s", "#"));
        if (colVis.showTimestamp)     sb.append(" │ ").append(String.format("%-8s", "Time"));
        if (colVis.showSender)        sb.append(" │ ").append(String.format("%-" + colWidths.sender + "s", "Sender"));
        if (colVis.showSenderHost)    sb.append(" │ ").append(String.format("%-" + colWidths.senderHost + "s", "Host"));
        if (colVis.showRecipient)     sb.append(" │ ").append(String.format("%-" + colWidths.recipient + "s", "Recipient"));
        if (colVis.showTopic)         sb.append(" │ ").append(String.format("%-" + colWidths.topic + "s", "Topic"));
        if (colVis.showSize)          sb.append(" │ ").append(String.format("%-8s", "Size"));
        if (colVis.showProcessed)     sb.append(" │ ").append(String.format("%-" + colWidths.processed + "s", "Processed"));
        if (colVis.showExclusive)     sb.append(" │ ").append(String.format("%-4s", "Excl"));
        if (colVis.showAnswer)        sb.append(" │ ").append(String.format("%-6s", "Ans"));
        if (colVis.showAnsweredBy)    sb.append(" │ ").append(String.format("%-" + colWidths.answeredBy + "s", "AnswerBy"));
        if (colVis.showAnsweredByHost)sb.append(" │ ").append(String.format("%-" + colWidths.answeredByHost + "s", "AnsHost"));
        if (colVis.showRtt)           sb.append(" │ ").append(String.format("%-11s", "RTT"));
        sb.append("\033[0m\n");
        sb.append("\033[36m").append(separator).append("\033[0m\n");

        // Message rows — tracker already returns newest-first; do NOT reverse again
        List<MessageInfo> messages = tracker.getMessagesNewestFirst();
        int rowNum = 0;
        long currentTime = System.currentTimeMillis();

        for (MessageInfo info : messages) {
            rowNum++;

            // Row color
            String rowColor = (rowNum % 2 == 0) ? "\033[97m" : "\033[37m";
            if (info.isTimedOut) {
                rowColor = "\033[91m";
            }

            boolean hasAnswer = info.rtt != null;
            String answerDisplay = hasAnswer ? "YES" : "";
            String rttStr = info.rtt != null ? formatRoundtrip(info.rtt) : "";
            String sizeStr = formatSize(info.size);
            String timeStr = formatRelativeTime(currentTime - info.timestamp);

            // Processed / lock / deleted display
            String processedStr = "";
            if (info.isDeleted) {
                processedStr = "DELETED";
            } else if (info.processedByCount > 0) {
                processedStr = info.processedByCount + " proc";
            } else if (info.lockedBy != null) {
                boolean lockExpired = info.lockedUntil != null && info.lockedUntil < currentTime;
                processedStr = (lockExpired ? "lock-exp:" : "locked:") + truncate(info.lockedBy, 8);
            } else if (hasAnswer) {
                if (info.isDeleteAfterProcessing && info.deleteAfterProcessingTime == 0) {
                    processedStr = "deleted after processing";
                } else if (info.isDeleteAfterProcessing && info.deleteAfterProcessingTime != 0) {
                    processedStr = "delete (after " + info.deleteAfterProcessingTime + "ms)";
                } else {
                    processedStr = "ans-only";
                }
            }

            sb.append(rowColor);
            sb.append(String.format("%-5d", rowNum));

            if (colVis.showTimestamp) {
                sb.append(" │ ").append(String.format("%-8s", timeStr));
            }
            if (colVis.showSender) {
                String versionColor = info.isV5 ? "\033[93m" : "\033[32m";
                String versionTag   = info.isV5 ? "[V5]" : "[V6]";
                String senderText   = truncate(info.sender, colWidths.sender - 4);
                String senderPad    = " ".repeat(Math.max(0, colWidths.sender - senderText.length() - 4));
                sb.append(" │ ").append(rowColor).append(senderText)
                  .append(versionColor).append(versionTag)
                  .append(rowColor).append(senderPad);
            }
            if (colVis.showSenderHost) {
                String versionColor = info.isV5 ? "\033[93m" : "\033[32m";
                String versionTag   = info.isV5 ? "[V5]" : "[V6]";
                String hostText = truncate(info.senderHost != null ? info.senderHost : "", colWidths.senderHost - 4);
                String hostPad  = " ".repeat(Math.max(0, colWidths.senderHost - hostText.length() - 4));
                sb.append(" │ ").append(rowColor).append(hostText)
                  .append(versionColor).append(versionTag)
                  .append(rowColor).append(hostPad);
            }
            if (colVis.showRecipient) {
                sb.append(" │ ").append(rowColor)
                  .append(String.format("%-" + colWidths.recipient + "s",
                          truncate(info.recipient, colWidths.recipient)));
            }
            if (colVis.showTopic) {
                String topicStr = info.topic;
                if (info.isDeleted) topicStr = "[DEL] " + topicStr;
                sb.append(" │ ").append(rowColor)
                  .append(String.format("%-" + colWidths.topic + "s",
                          truncate(topicStr, colWidths.topic)));
            }
            if (colVis.showSize) {
                sb.append(" │ ").append(rowColor).append(String.format("%-8s", sizeStr));
            }
            if (colVis.showProcessed) {
                sb.append(" │ ").append(rowColor)
                  .append(String.format("%-" + colWidths.processed + "s", processedStr));
            }
            if (colVis.showExclusive) {
                sb.append(" │ ").append(rowColor).append(String.format("%-4s", info.isExclusive ? "X" : ""));
            }
            if (colVis.showAnswer) {
                sb.append(" │ ");
                if (hasAnswer) {
                    sb.append("\033[32m").append(String.format("%-3s", answerDisplay))
                      .append("\033[0m").append(rowColor);
                } else {
                    sb.append(rowColor).append(String.format("%-3s", answerDisplay));
                }
            }
            if (colVis.showAnsweredBy) {
                if (info.answeredBy != null) {
                    String versionMarker = info.answerIsV5 ? "\033[93m[V5]\033[0m" : "\033[32m[V6]\033[0m";
                    String answeredByText = truncate(info.answeredBy, colWidths.answeredBy - 4);
                    String answeredByPad  = " ".repeat(Math.max(0, colWidths.answeredBy - answeredByText.length() - 4));
                    sb.append(" │ ").append(rowColor).append(answeredByText)
                      .append(versionMarker).append(rowColor).append(answeredByPad);
                } else {
                    sb.append(" │ ").append(rowColor).append(" ".repeat(colWidths.answeredBy));
                }
            }
            if (colVis.showAnsweredByHost) {
                if (info.answeredByHost != null) {
                    String versionMarker   = info.answerIsV5 ? "\033[93m[V5]\033[0m" : "\033[32m[V6]\033[0m";
                    String answeredByHostText = truncate(info.answeredByHost, colWidths.answeredByHost - 4);
                    String answeredByHostPad  = " ".repeat(Math.max(0, colWidths.answeredByHost - answeredByHostText.length() - 4));
                    sb.append(" │ ").append(rowColor).append(answeredByHostText)
                      .append(versionMarker).append(rowColor).append(answeredByHostPad);
                } else {
                    sb.append(" │ ").append(rowColor).append(" ".repeat(colWidths.answeredByHost));
                }
            }
            if (colVis.showRtt) {
                sb.append(" │ ").append(rowColor).append(String.format("%-11s", rttStr));
            }

            sb.append("\033[0m\n");
        }

        // Ghost-line fill to avoid leftover characters from prior frames
        int currentMaxMessages = termState.maxMessages;
        for (int i = tracker.getBufferSize(); i < currentMaxMessages; i++) {
            sb.append(" ".repeat(currentWidth - 1)).append("\n");
        }

        // Footer
        sb.append("\033[36m").append(separator).append("\033[0m\n");
        sb.append("\033[90mPress Ctrl+C to exit\033[0m");
        sb.append("\033[J");

        System.out.print(sb.toString());
        System.out.flush();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private String formatRelativeTime(long ageMs) {
        long seconds = ageMs / 1000;
        if (seconds < 60)   return seconds + "s";
        else if (seconds < 3600) return (seconds / 60) + "m";
        else                return (seconds / 3600) + "h";
    }

    private String formatSize(int bytes) {
        if (bytes < 1024)           return bytes + "B";
        else if (bytes < 1024 * 1024) return String.format("%.1fK", bytes / 1024.0);
        else                         return String.format("%.1fM", bytes / (1024.0 * 1024.0));
    }

    private String formatRoundtrip(long ms) {
        if (ms < 1000)  return ms + "ms";
        else if (ms < 60000) return String.format("%.2fs", ms / 1000.0);
        else            return String.format("%.1fm", ms / 60000.0);
    }

    private String truncate(String str, int maxLen) {
        if (str == null || str.isEmpty()) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, Math.max(0, maxLen - 1)) + "…";
    }
}

package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.core.MessageFeed;
import de.caluga.morpheus.core.MessageInfo;
import de.caluga.morpheus.core.MessageStats;
import de.caluga.morpheus.core.MessageTracker;
import de.caluga.morpheus.tui.Screen;


/** Live message table rendered on Lanterna; same core/ data as the CLI monitor. */
public class MessagesScreen implements Screen {

    private final MessageTracker tracker;
    private MorpheusContext ownedCtx;

    /** Production: connect a feed (ctx must already be connected by the launcher). */
    public MessagesScreen(MorpheusContext ctx) {
        this.tracker = new MessageTracker(100);
        // Guard so the screen can also be constructed with an unconnected context (unit tests,
        // disabled paths): only start the feed when there is a live Morphium.
        if (ctx.getMorphium() != null) {
            this.ownedCtx = ctx;
            MessageFeed feed = new MessageFeed(ctx.getMorphium(), ctx.getMessaging(),
                    tracker, () -> {}, false);
            Thread t = new Thread(() -> { try { feed.watch(); } catch (Throwable ignored) {} }, "messages-feed");
            t.setDaemon(true);
            t.start();
        }
    }

    /** Test seam: render a hand-fed tracker without a DB feed. */
    MessagesScreen(MessageTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public void onClose() {
        if (ownedCtx != null) ownedCtx.close();
    }

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) return Result.stay();
        if (key.getCharacter() != null && key.getCharacter() == 'q') return Result.quit();
        if (key.getKeyType() == com.googlecode.lanterna.input.KeyType.Escape) return Result.pop();
        return Result.stay();
    }

    @Override
    public void draw(TextGraphics g) {
        int width = g.getSize().getColumns();
        int height = g.getSize().getRows();
        MessageStats stats = tracker.getStats();

        // Stats header
        long uptime = (System.currentTimeMillis() - stats.getStartTime()) / 1000;
        long msgs = stats.getTotalMessages();
        double perSec = uptime > 0 ? msgs / (double) uptime : 0;
        g.setForegroundColor(TextColor.ANSI.YELLOW);
        g.putString(2, 0, trunc(String.format(
                "Uptime: %ds | Messages: %d (%.1f/s) | Answers: %d | Updates: %d | Timeouts: %d | Buffer: %d/%d",
                uptime, msgs, perSec, stats.getTotalAnswers(), stats.getTotalUpdates(),
                stats.getTotalTimeouts(), tracker.getBufferSize(), 100), width - 3));

        // Summary line: slowest topics
        g.setForegroundColor(TextColor.ANSI.MAGENTA);
        StringBuilder rtt = new StringBuilder("Ø-RTT/Topic: ");
        for (var t : stats.getSlowestTopics(5)) rtt.append(trunc(t.topic(), 15)).append(":").append(t.avgRtt()).append("ms ");
        g.putString(2, 1, trunc(rtt.toString(), width - 3));

        // Summary line: most active hosts / senders (ported from the CLI monitor)
        g.setForegroundColor(TextColor.ANSI.CYAN);
        StringBuilder act = new StringBuilder("Aktiv — Hosts: ");
        for (var h : stats.getTopHosts(5)) act.append(trunc(h.name(), 18)).append("(").append(h.count()).append(") ");
        act.append(" | Sender: ");
        for (var sd : stats.getTopSenders(5)) act.append(trunc(sd.name(), 18)).append("(").append(sd.count()).append(") ");
        g.putString(2, 2, trunc(act.toString(), width - 3));

        // Column widths from terminal width
        int[] w = columnWidths(width);
        int cSender = w[0], cHost = w[1], cTopic = w[2], cProc = w[3], cAnsBy = w[4], cAnsHost = w[5];

        // Header row
        g.setForegroundColor(TextColor.ANSI.CYAN);
        String header = String.format("%-4s %-7s %-" + cSender + "s %-" + cHost + "s %-" + cTopic
                + "s %-6s %-" + cProc + "s %-3s %-3s %-" + cAnsBy + "s %-" + cAnsHost + "s %-9s",
                "#", "Time", "Sender", "Host", "Topic", "Size", "Proc", "Ex", "An",
                "AnswerBy", "AnsHost", "RTT");
        g.putString(2, 3, trunc(header, width - 3));

        // Rows newest-first
        var messages = tracker.getMessagesNewestFirst();
        long now = System.currentTimeMillis();
        int row = 4;
        int maxRows = height - 6;
        int n = 0;
        for (MessageInfo info : messages) {
            if (n >= maxRows) break;
            n++;
            if (info.isTimedOut) g.setForegroundColor(TextColor.ANSI.RED);
            else g.setForegroundColor(n % 2 == 0 ? TextColor.ANSI.WHITE : TextColor.ANSI.DEFAULT);
            String time = ((now - info.timestamp) / 1000) + "s";
            String ver = info.isV5 ? "5" : "6";
            String sender = trunc((info.sender == null ? "" : info.sender) + "·v" + ver, cSender);
            String host = trunc(info.senderHost, cHost);
            String topic = trunc(info.isDeleted ? "[DEL]" + info.topic : info.topic, cTopic);
            String size = info.size + "B";
            String proc = trunc(procStr(info, now), cProc);
            String ex = info.isExclusive ? "X" : "";
            String an = info.rtt != null ? "Y" : "";
            String ansBy = trunc(info.answeredBy, cAnsBy);
            String ansHost = trunc(info.answeredByHost, cAnsHost);
            String rttStr = info.rtt != null ? info.rtt + "ms" : "";
            String line = String.format("%-4d %-7s %-" + cSender + "s %-" + cHost + "s %-" + cTopic
                    + "s %-6s %-" + cProc + "s %-3s %-3s %-" + cAnsBy + "s %-" + cAnsHost + "s %-9s",
                    n, time, sender, host, topic, size, proc, ex, an, ansBy, ansHost, rttStr);
            g.putString(2, row++, trunc(line, width - 3));
        }

        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, height - 1, "[esc] zurück  [q] quit");
    }

    /** Distributes terminal width across the variable columns (sender, host, topic, proc, ansBy, ansHost). */
    private int[] columnWidths(int termWidth) {
        // fixed: #(4) Time(7) Size(6) Ex(3) An(3) RTT(9) + 11 single-space separators ≈ 43
        int fixed = 4 + 7 + 6 + 3 + 3 + 9 + 12;
        int available = Math.max(36, termWidth - fixed - 3);
        int per = available / 6;
        int sender = Math.max(8, per);
        int host = Math.max(8, per);
        int topic = Math.max(10, per);
        int proc = Math.max(8, per);
        int ansBy = Math.max(8, per);
        int ansHost = Math.max(8, per);
        return new int[]{sender, host, topic, proc, ansBy, ansHost};
    }

    /** Processed/lock/answer status string, mirroring the CLI monitor's precedence. */
    private String procStr(MessageInfo info, long now) {
        if (info.isDeleted) return "DELETED";
        if (info.processedByCount > 0) return info.processedByCount + " proc";
        if (info.lockedBy != null) {
            boolean expired = info.lockedUntil != null && info.lockedUntil < now;
            return (expired ? "lock-exp:" : "locked:") + trunc(info.lockedBy, 8);
        }
        if (info.rtt != null) return "ans-only";
        return "";
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        if (max <= 1) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

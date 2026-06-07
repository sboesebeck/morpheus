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
        tracker.markTimeouts(System.currentTimeMillis(), 2000);
        int width = g.getSize().getColumns();
        int height = g.getSize().getRows();
        MessageStats stats = tracker.getStats();

        // Title bar (centered, blue)
        String title = "MORPHEUS MESSAGE MONITOR";
        g.setForegroundColor(TextColor.ANSI.BLUE_BRIGHT);
        g.putString(Math.max(0, (width - title.length()) / 2), 0, title);

        // Stats header (yellow)
        long uptime = (System.currentTimeMillis() - stats.getStartTime()) / 1000;
        long msgs = stats.getTotalMessages();
        double perSec = uptime > 0 ? msgs / (double) uptime : 0;
        g.setForegroundColor(TextColor.ANSI.YELLOW);
        g.putString(2, 1, trunc(String.format(
                "Uptime: %ds | Messages: %d (%.1f/s) | Answers: %d | Updates: %d | Timeouts: %d | Buffer: %d/%d",
                uptime, msgs, perSec, stats.getTotalAnswers(), stats.getTotalUpdates(),
                stats.getTotalTimeouts(), tracker.getBufferSize(), 100), width - 3));

        // Summary line: slowest topics (magenta)
        g.setForegroundColor(TextColor.ANSI.MAGENTA);
        StringBuilder rtt = new StringBuilder("Ø-RTT/Topic: ");
        for (var t : stats.getSlowestTopics(5)) rtt.append(trunc(t.topic(), 15)).append(":").append(t.avgRtt()).append("ms ");
        g.putString(2, 2, trunc(rtt.toString(), width - 3));

        // Summary line: most active hosts / senders (cyan)
        g.setForegroundColor(TextColor.ANSI.CYAN);
        StringBuilder act = new StringBuilder("Aktiv — Hosts: ");
        for (var h : stats.getTopHosts(5)) act.append(trunc(h.name(), 18)).append("(").append(h.count()).append(") ");
        act.append(" | Sender: ");
        for (var sd : stats.getTopSenders(5)) act.append(trunc(sd.name(), 18)).append("(").append(sd.count()).append(") ");
        g.putString(2, 3, trunc(act.toString(), width - 3));

        // Column widths from terminal width
        int[] w = columnWidths(width);
        int cSender = w[0], cHost = w[1], cTopic = w[2], cProc = w[3], cAnsBy = w[4], cAnsHost = w[5];

        // Top rule
        String rule = "─".repeat(Math.max(0, width - 3));
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 4, rule);

        // Header row (cyan) with │ separators
        g.setForegroundColor(TextColor.ANSI.CYAN);
        String header = String.format("%-4s │ %-7s │ %-" + cSender + "s │ %-" + cHost + "s │ %-" + cTopic
                + "s │ %-6s │ %-" + cProc + "s │ %-3s │ %-3s │ %-" + cAnsBy + "s │ %-" + cAnsHost + "s │ %-9s",
                "#", "Time", "Sender", "Host", "Topic", "Size", "Proc", "Ex", "An",
                "AnswerBy", "AnsHost", "RTT");
        g.putString(2, 5, trunc(header, width - 3));

        // Bottom rule
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 6, rule);

        // Rows newest-first, cell-by-cell so version tags can be colored independently
        var messages = tracker.getMessagesNewestFirst();
        long now = System.currentTimeMillis();
        int dataStart = 7;
        int row = dataStart;
        int maxRows = Math.max(0, height - dataStart - 1);
        int n = 0;
        for (MessageInfo info : messages) {
            if (n >= maxRows) break;
            n++;
            TextColor base = info.isTimedOut ? TextColor.ANSI.RED
                    : (n % 2 == 0 ? TextColor.ANSI.WHITE : TextColor.ANSI.DEFAULT);
            drawRow(g, row++, width, n, base, info, now, cSender, cHost, cTopic, cProc, cAnsBy, cAnsHost);
        }

        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, height - 1, "[esc] zurück  [q] quit");
    }

    /** V5 → yellow, V6 → green (independent of the row's base color). */
    static TextColor tagColor(boolean isV5) {
        return isV5 ? TextColor.ANSI.YELLOW : TextColor.ANSI.GREEN;
    }

    /** Draws one message row cell-by-cell with │ separators and colored [V5]/[V6] tags. */
    private void drawRow(TextGraphics g, int y, int width, int n, TextColor base, MessageInfo info, long now,
                         int cSender, int cHost, int cTopic, int cProc, int cAnsBy, int cAnsHost) {
        int maxX = width - 1;
        int x = 2;
        String time = ((now - info.timestamp) / 1000) + "s";
        String topic = info.isDeleted ? "[DEL]" + info.topic : info.topic;
        String size = info.size + "B";
        String proc = procStr(info, now);
        String ex = info.isExclusive ? "X" : "";
        String an = info.rtt != null ? "Y" : "";
        String rttStr = info.rtt != null ? info.rtt + "ms" : "";

        x = field(g, x, y, maxX, String.valueOf(n), 4, base);
        x = sep(g, x, y, maxX);
        x = field(g, x, y, maxX, time, 7, base);
        x = sep(g, x, y, maxX);
        x = taggedField(g, x, y, maxX, info.sender == null ? "" : info.sender, info.isV5, cSender, base);
        x = sep(g, x, y, maxX);
        x = taggedField(g, x, y, maxX, info.senderHost == null ? "" : info.senderHost, info.isV5, cHost, base);
        x = sep(g, x, y, maxX);
        x = field(g, x, y, maxX, topic, cTopic, base);
        x = sep(g, x, y, maxX);
        x = field(g, x, y, maxX, size, 6, base);
        x = sep(g, x, y, maxX);
        x = field(g, x, y, maxX, proc, cProc, base);
        x = sep(g, x, y, maxX);
        x = field(g, x, y, maxX, ex, 3, base);
        x = sep(g, x, y, maxX);
        x = field(g, x, y, maxX, an, 3, base);
        x = sep(g, x, y, maxX);
        if (info.answeredBy != null) {
            x = taggedField(g, x, y, maxX, info.answeredBy, info.answerIsV5, cAnsBy, base);
        } else {
            x = field(g, x, y, maxX, "", cAnsBy, base);
        }
        x = sep(g, x, y, maxX);
        if (info.answeredBy != null && info.answeredByHost != null) {
            x = taggedField(g, x, y, maxX, info.answeredByHost, info.answerIsV5, cAnsHost, base);
        } else {
            x = field(g, x, y, maxX, "", cAnsHost, base);
        }
        x = sep(g, x, y, maxX);
        field(g, x, y, maxX, rttStr, 9, base);
    }

    /** Writes a left-justified field of width w in `color`, clipped to maxX; returns the new x. */
    private int field(TextGraphics g, int x, int y, int maxX, String s, int w, TextColor color) {
        if (x >= maxX) return x;
        String text = pad(trunc(s, w), w);
        if (x + text.length() > maxX) text = text.substring(0, Math.max(0, maxX - x));
        g.setForegroundColor(color);
        g.putString(x, y, text);
        return x + text.length();
    }

    /** Writes a field whose last 4 chars are a colored [V5]/[V6] tag; total width w; returns new x. */
    private int taggedField(TextGraphics g, int x, int y, int maxX, String text, boolean isV5, int w, TextColor base) {
        int textW = Math.max(0, w - 4);
        int nx = field(g, x, y, maxX, text, textW, base);
        if (nx < maxX) {
            String tag = isV5 ? "[V5]" : "[V6]";
            if (nx + tag.length() > maxX) tag = tag.substring(0, Math.max(0, maxX - nx));
            g.setForegroundColor(tagColor(isV5));
            g.putString(nx, y, tag);
            nx += tag.length();
        }
        return nx;
    }

    /** Writes the " │ " separator in a dim color, clipped to maxX; returns the new x. */
    private int sep(TextGraphics g, int x, int y, int maxX) {
        if (x >= maxX) return x;
        String s = " │ ";
        if (x + s.length() > maxX) s = s.substring(0, Math.max(0, maxX - x));
        g.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        g.putString(x, y, s);
        return x + s.length();
    }

    /** Right-pads s with spaces to width w (no truncation; callers trunc first). */
    private String pad(String s, int w) {
        if (s.length() >= w) return s;
        return s + " ".repeat(w - s.length());
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

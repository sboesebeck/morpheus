package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.config.ConfigurationManager;
import de.caluga.morpheus.core.MessageFeed;
import de.caluga.morpheus.core.MessageFilter;
import de.caluga.morpheus.core.MessageInfo;
import de.caluga.morpheus.core.MessageStats;
import de.caluga.morpheus.core.MessageTracker;
import de.caluga.morpheus.tui.Screen;
import de.caluga.morpheus.tui.widget.PickerOverlay;
import de.caluga.morphium.driver.MorphiumId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


/** Live message table rendered on Lanterna; same core/ data as the CLI monitor. */
public class MessagesScreen implements Screen {

    static final int DEFAULT_HISTORY_CAPACITY = ConfigurationManager.DEFAULT_MESSAGE_HISTORY_SIZE;
    private static final int[] HISTORY_PRESETS = {100, 250, 500, 1_000, 2_500, 5_000, 10_000};

    private final MessageTracker tracker;
    private final ConfigurationManager config;
    private MorpheusContext ownedCtx;
    private MessageFeed feed;
    private final MessageFilter filter = new MessageFilter();
    private enum Axis { TOPIC, SENDER, HOST }
    private Axis pickerAxis;
    private PickerOverlay picker;            // null = closed
    private boolean regexEditing = false;
    private String regexBuffer = "";
    private MorphiumId selectedId;
    private int selected = 0;
    private int scroll = 0;
    private int visibleRows = 1;
    private String historyError;
    private boolean paused = false;
    private List<MessageInfo> pausedMessages = List.of();
    private long pausedAtMessageCount = 0;

    /** Production: connect a feed (ctx must already be connected by the launcher). */
    public MessagesScreen(MorpheusContext ctx) {
        this.config = ctx.getConfig();
        this.tracker = new MessageTracker(config.getMessageHistorySize());
        // Guard so the screen can also be constructed with an unconnected context (unit tests,
        // disabled paths): only start the feed when there is a live Morphium.
        if (ctx.getMorphium() != null) {
            this.ownedCtx = ctx;
            this.feed = new MessageFeed(ctx.getMorphium(), ctx.getMessaging(),
                    tracker, () -> {}, false);
            this.feed.start();
        }
    }

    /** Test seam: render a hand-fed tracker without a DB feed. */
    MessagesScreen(MessageTracker tracker) {
        this.tracker = tracker;
        this.config = null;
    }

    @Override
    public void onClose() {
        if (feed != null) feed.stop();
        if (ownedCtx != null) ownedCtx.close();
    }

    MessageFilter filter() { return filter; }

    boolean regexEditing() { return regexEditing; }

    int selectedIndex() { return selected; }

    int scrollOffset() { return scroll; }

    MorphiumId selectedMessageId() { return selectedId; }

    int historyCapacity() { return tracker.getMaxMessages(); }

    boolean paused() { return paused; }

    long messagesSincePause() {
        return paused ? Math.max(0, tracker.getStats().getTotalMessages() - pausedAtMessageCount) : 0;
    }

    /** Number of rows that would render for a terminal of `termRows` height, after filtering. */
    int visibleRowCount(int termRows) {
        int dataStart = 7;
        int maxRows = Math.max(0, termRows - dataStart - 1);
        int n = 0;
        for (MessageInfo ignored : filteredMessages()) {
            if (n >= maxRows) break;
            n++;
        }
        return n;
    }

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) return Result.stay();
        if (regexEditing) {
            if (key.getKeyType() == KeyType.Enter) {
                filter.bodyRegex = regexBuffer.isEmpty() ? null : regexBuffer;
                regexEditing = false;
                resetSelection();
                return Result.stay();
            }
            if (key.getKeyType() == KeyType.Escape) { regexEditing = false; regexBuffer = ""; return Result.stay(); }
            if (key.getKeyType() == KeyType.Backspace) {
                if (!regexBuffer.isEmpty()) regexBuffer = regexBuffer.substring(0, regexBuffer.length() - 1);
                return Result.stay();
            }
            Character rc = key.getCharacter();
            if (rc != null && !Character.isISOControl(rc)) regexBuffer += rc;
            return Result.stay();
        }
        if (picker != null) {
            PickerOverlay.Outcome o = picker.onKey(key);
            if (o == PickerOverlay.Outcome.COMMIT) { commitPicker(); picker = null; }
            else if (o == PickerOverlay.Outcome.CANCEL) { picker = null; }
            return Result.stay();
        }
        Character c = key.getCharacter();
        if (c != null && c == 'q') return Result.quit();
        if (c != null && c == 't') { openPicker(Axis.TOPIC); return Result.stay(); }
        if (c != null && c == 's') { openPicker(Axis.SENDER); return Result.stay(); }
        if (c != null && c == 'o') { openPicker(Axis.HOST); return Result.stay(); }
        if (c != null && c == 'c') { filter.clear(); resetSelection(); return Result.stay(); }
        if (c != null && c == 'a') { filter.cycle("answered"); resetSelection(); return Result.stay(); }
        if (c != null && c == 'x') { filter.cycle("exclusive"); resetSelection(); return Result.stay(); }
        if (c != null && c == 'e') { filter.cycle("timedOut"); resetSelection(); return Result.stay(); }
        if (c != null && c == 'd') { filter.cycle("deleted"); resetSelection(); return Result.stay(); }
        if (c != null && c == '+') { stepHistory(1); return Result.stay(); }
        if (c != null && c == '-') { stepHistory(-1); return Result.stay(); }
        if (c != null && c == 'p') { togglePause(); return Result.stay(); }
        if (c != null && c == '/') { regexEditing = true; regexBuffer = filter.bodyRegex == null ? "" : filter.bodyRegex; return Result.stay(); }
        if (key.getKeyType() == KeyType.ArrowDown) { navigate(1); return Result.stay(); }
        if (key.getKeyType() == KeyType.ArrowUp) { navigate(-1); return Result.stay(); }
        if (key.getKeyType() == KeyType.PageDown) { navigate(visibleRows); return Result.stay(); }
        if (key.getKeyType() == KeyType.PageUp) { navigate(-visibleRows); return Result.stay(); }
        if (key.getKeyType() == KeyType.End) { resumeLive(); return Result.stay(); }
        if (key.getKeyType() == KeyType.Enter) {
            MessageInfo info = selectedMessage();
            return info == null ? Result.stay() : Result.push(new MessageDetailScreen(info));
        }
        if (key.getKeyType() == KeyType.Escape) return Result.pop();
        return Result.stay();
    }

    /** Builds the picker for an axis from the distinct values seen in the stats. */
    private void openPicker(Axis axis) {
        pickerAxis = axis;
        List<String> items = new ArrayList<>();
        Map<String, String> labels = new HashMap<>();
        Set<String> initial = new LinkedHashSet<>();
        MessageStats stats = tracker.getStats();
        if (axis == Axis.TOPIC) {
            for (MessageStats.TopicAggregate a : stats.getTopicStats(Integer.MAX_VALUE)) items.add(a.topic());
            initial.addAll(filter.topics);
        } else if (axis == Axis.SENDER) {
            for (MessageStats.NamedCount nc : stats.getTopSenders(Integer.MAX_VALUE)) items.add(nc.name());
            initial.addAll(filter.senders);
        } else {
            for (MessageStats.NamedCount nc : stats.getTopHosts(Integer.MAX_VALUE)) items.add(nc.name());
            initial.addAll(filter.hosts);
        }
        java.util.Collections.sort(items);
        for (String it : items) labels.put(it, it);
        String title = axis == Axis.TOPIC ? "Topic" : axis == Axis.SENDER ? "Sender" : "Host";
        picker = new PickerOverlay(title, items, labels, initial);
    }

    private void commitPicker() {
        Set<String> target = pickerAxis == Axis.TOPIC ? filter.topics
                : pickerAxis == Axis.SENDER ? filter.senders : filter.hosts;
        target.clear();
        target.addAll(picker.selection());
        resetSelection();
    }

    private List<MessageInfo> filteredMessages() {
        List<MessageInfo> result = new ArrayList<>();
        List<MessageInfo> source = paused ? pausedMessages : tracker.getMessagesNewestFirst();
        for (MessageInfo info : source) {
            if (filter.accepts(info)) result.add(info);
        }
        return result;
    }

    private MessageInfo selectedMessage() {
        List<MessageInfo> messages = filteredMessages();
        syncSelection(messages);
        return messages.isEmpty() ? null : messages.get(selected);
    }

    private void moveSelection(int delta) {
        List<MessageInfo> messages = filteredMessages();
        syncSelection(messages);
        if (messages.isEmpty()) return;
        selected = Math.max(0, Math.min(messages.size() - 1, selected + delta));
        selectedId = selected == 0 ? null : messages.get(selected).id;
    }

    private void navigate(int delta) {
        if (!paused) {
            if (delta <= 0) return;
            pause();
        }
        moveSelection(delta);
    }

    private void togglePause() {
        if (paused) resumeLive();
        else pause();
    }

    private void pause() {
        if (paused) return;
        pausedMessages = tracker.getMessagesNewestFirst();
        pausedAtMessageCount = tracker.getStats().getTotalMessages();
        paused = true;
        syncSelection(filteredMessages());
    }

    private void resumeLive() {
        paused = false;
        pausedMessages = List.of();
        pausedAtMessageCount = 0;
        resetSelection();
    }

    private void syncSelection(List<MessageInfo> messages) {
        if (messages.isEmpty()) {
            resetSelection();
            return;
        }
        if (selectedId == null) {
            selected = 0;
            return;
        }
        for (int i = 0; i < messages.size(); i++) {
            if (selectedId.equals(messages.get(i).id)) {
                selected = i;
                return;
            }
        }
        selected = Math.min(selected, messages.size() - 1);
        selectedId = selected == 0 ? null : messages.get(selected).id;
    }

    private void resetSelection() {
        selectedId = null;
        selected = 0;
        scroll = 0;
    }

    private void stepHistory(int direction) {
        int current = tracker.getMaxMessages();
        int target = current;
        if (direction > 0) {
            for (int preset : HISTORY_PRESETS) {
                if (preset > current) { target = preset; break; }
            }
        } else {
            for (int i = HISTORY_PRESETS.length - 1; i >= 0; i--) {
                if (HISTORY_PRESETS[i] < current) { target = HISTORY_PRESETS[i]; break; }
            }
        }
        if (target == current) return;
        tracker.setMaxMessages(target);
        if (paused && pausedMessages.size() > target) {
            pausedMessages = new ArrayList<>(pausedMessages.subList(0, target));
        }
        syncSelection(filteredMessages());
        if (config == null) return;
        try {
            config.setMessageHistorySize(target);
            historyError = null;
        } catch (Exception ignored) {
            historyError = "History konnte nicht gespeichert werden";
        }
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
                stats.getTotalTimeouts(), tracker.getBufferSize(), tracker.getMaxMessages()), width - 3));

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
        var messages = filteredMessages();
        syncSelection(messages);
        long now = System.currentTimeMillis();
        int dataStart = 7;
        int row = dataStart;
        int maxRows = Math.max(0, height - dataStart - 1);
        visibleRows = Math.max(1, maxRows);
        if (selected < scroll) scroll = selected;
        else if (selected >= scroll + visibleRows) scroll = selected - visibleRows + 1;
        int maxScroll = Math.max(0, messages.size() - visibleRows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        for (int i = scroll; i < messages.size() && row - dataStart < maxRows; i++) {
            MessageInfo info = messages.get(i);
            TextColor base = i == selected ? TextColor.ANSI.GREEN_BRIGHT
                    : info.isTimedOut ? TextColor.ANSI.RED
                    : ((i + 1) % 2 == 0 ? TextColor.ANSI.WHITE : TextColor.ANSI.DEFAULT);
            drawRow(g, row++, width, i + 1, i == selected, base, info, now,
                    cSender, cHost, cTopic, cProc, cAnsBy, cAnsHost);
        }

        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, height - 1, trunc(statusLine(), width - 3));
        if (regexEditing) {
            g.setForegroundColor(TextColor.ANSI.YELLOW);
            g.putString(2, height - 1, trunc("Regex: " + regexBuffer + "▌   [enter] ok  [esc] abbrechen", width - 3));
        }
        if (picker != null) picker.draw(g);
    }

    /** V5 → yellow, V6 → green (independent of the row's base color). */
    static TextColor tagColor(boolean isV5) {
        return isV5 ? TextColor.ANSI.YELLOW : TextColor.ANSI.GREEN;
    }

    /** Draws one message row cell-by-cell with │ separators and colored [V5]/[V6] tags. */
    private void drawRow(TextGraphics g, int y, int width, int n, boolean selectedRow,
                         TextColor base, MessageInfo info, long now,
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

        x = field(g, x, y, maxX, (selectedRow ? "▶" : "") + n, 4, base);
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
        // fixed: #(4) Time(7) Size(6) Ex(3) An(3) RTT(9) = 32, plus 11 " │ " separators (11*3=33)
        int fixed = 4 + 7 + 6 + 3 + 3 + 9 + 33;
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

    /** Compact one-line summary of active filters + key hints. */
    private String statusLine() {
        StringBuilder sb = new StringBuilder();
        if (!filter.isEmpty()) {
            sb.append("Filter:");
            if (!filter.topics.isEmpty())  sb.append(" topic[").append(filter.topics.size()).append("]");
            if (!filter.senders.isEmpty()) sb.append(" sender[").append(filter.senders.size()).append("]");
            if (!filter.hosts.isEmpty())   sb.append(" host[").append(filter.hosts.size()).append("]");
            sb.append(triLabel("ans", filter.answered));
            sb.append(triLabel("timeout", filter.timedOut));
            sb.append(triLabel("excl", filter.exclusive));
            sb.append(triLabel("del", filter.deleted));
            if (filter.bodyRegex != null && !filter.bodyRegex.isEmpty()) {
                sb.append(filter.regexValid() ? "  /" + filter.bodyRegex + "/" : "  /" + filter.bodyRegex + "/⚠");
            }
            sb.append("   ");
        }
        if (historyError != null) sb.append(historyError).append("   ");
        if (paused) sb.append("PAUSE +").append(messagesSincePause()).append(" neu [p]live   ");
        else sb.append("LIVE [p]pause   ");
        String pos = filteredMessages().isEmpty() ? "0/0" : (selected + 1) + "/" + filteredMessages().size();
        sb.append(pos).append("  hist:").append(tracker.getMaxMessages())
                .append(" [+/-]  [↑↓/PgUp/Dn] [enter]detail  [t]opic [s]ender h[o]st  ")
                .append("[a]ns [x]excl [e]xpire [d]el  [/]regex [c]lear [esc][q]");
        return sb.toString();
    }

    private String triLabel(String name, MessageFilter.TriState t) {
        return switch (t) {
            case NONE -> "";
            case ONLY -> " " + name + ":nur";
            case EXCLUDE -> " ⊘" + name;
        };
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        if (max <= 1) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

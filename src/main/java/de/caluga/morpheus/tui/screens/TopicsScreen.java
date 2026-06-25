package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.core.MessageFeed;
import de.caluga.morpheus.core.MessageStats;
import de.caluga.morpheus.core.MessageTracker;
import de.caluga.morpheus.core.NodeRegistry;
import de.caluga.morpheus.core.NodeStatus;
import de.caluga.morpheus.core.StatusPinger;
import de.caluga.morpheus.tui.Screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-topic view: topics discovered via a periodic ALL ping (listeners), activity counted from
 *  the stream. Shows the union of ping- and stream-topics; flags topics with traffic but no
 *  listener (sender firing into the void). Drilldown lists the listener nodes of a topic. */
public class TopicsScreen implements Screen {

    private static final int[] INTERVAL_PRESETS = {5_000, 10_000, 15_000, 30_000};
    private static final int PING_TIMEOUT_MS = 5_000;

    private final MessageTracker tracker;
    private final NodeRegistry registry;
    private MorpheusContext ownedCtx;
    private StatusPinger pinger;                      // null in the test seam

    private volatile List<NodeStatus> pendingRoster;  // ping handoff to the UI thread
    private volatile boolean pinging = false;
    private volatile long lastPing = 0;
    private int intervalIndex = 1;                     // default 10s
    private int rowCount = 0;                          // last rendered row count (for clamping)
    private int selected = 0;
    private int scroll = 0;          // index of the first visible row
    private int visibleRows = 1;     // data rows that fit, set during draw

    enum SortKey { TOPIC, MESSAGES, ANSWERS, RTT, TIMEOUTS, LISTENERS }
    private SortKey sortKey = SortKey.MESSAGES;
    private boolean sortDesc = true;

    /** One topic row: stream counters + listener count from the ping. */
    record Row(String topic, long messages, long answers, long avgRtt, long timeouts, int listeners) {
        boolean firingIntoVoid() { return messages > 0 && listeners == 0; }
    }

    public TopicsScreen(MorpheusContext ctx) {
        this.tracker = new MessageTracker(500);
        String self = ctx.getMessaging() != null ? ctx.getMessaging().getSenderId() : "";
        this.registry = new NodeRegistry(self);
        if (ctx.getMorphium() != null) {
            this.ownedCtx = ctx;
            this.pinger = ctx.getMessaging() != null ? new StatusPinger(ctx.getMessaging()) : null;
            MessageFeed feed = new MessageFeed(ctx.getMorphium(), ctx.getMessaging(), tracker, () -> {}, false);
            Thread t = new Thread(() -> { try { feed.watch(); } catch (Throwable ignored) {} }, "topics-feed");
            t.setDaemon(true);
            t.start();
            refresh();
        }
    }

    /** Test seam: fixed tracker + registry, no backend. */
    TopicsScreen(MessageTracker tracker, NodeRegistry registry) {
        this.tracker = tracker;
        this.registry = registry;
    }

    int currentIntervalMs() { return INTERVAL_PRESETS[intervalIndex]; }   // test seam
    int selectedIndex() { return selected; }                             // test seam
    int scrollOffset() { return scroll; }                               // test seam
    SortKey sortKey() { return sortKey; }                                // test seam
    boolean sortDescending() { return sortDesc; }                        // test seam

    @Override
    public void onClose() {
        if (ownedCtx != null) ownedCtx.close();
    }

    /** Starts one background ping (no-op if already pinging or no backend). */
    private void refresh() {
        if (pinger == null || pinging) return;
        pinging = true;
        Thread t = new Thread(() -> {
            try {
                List<NodeStatus> r = pinger.ping(PING_TIMEOUT_MS);
                if (!r.isEmpty()) pendingRoster = r;
            } catch (Throwable ignored) {
            } finally {
                pinging = false;
                lastPing = System.currentTimeMillis();
            }
        }, "topics-ping");
        t.setDaemon(true);
        t.start();
    }

    private void stepInterval(int dir) {
        intervalIndex = Math.max(0, Math.min(INTERVAL_PRESETS.length - 1, intervalIndex + dir));
    }

    private static SortKey cycleSort(SortKey k) {
        SortKey[] v = SortKey.values();
        return v[(k.ordinal() + 1) % v.length];
    }

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) {
            if (pinger != null && System.currentTimeMillis() - lastPing > currentIntervalMs()) refresh();
            return Result.stay();
        }
        Character c = key.getCharacter();
        if (c != null && c == 'q') return Result.quit();
        if (c != null && c == 'r') { refresh(); return Result.stay(); }
        if (c != null && c == '+') { stepInterval(+1); return Result.stay(); }
        if (c != null && c == '-') { stepInterval(-1); return Result.stay(); }
        if (c != null && c == 's') { sortKey = cycleSort(sortKey); return Result.stay(); }
        if (c != null && c == 'S') { sortDesc = !sortDesc; return Result.stay(); }
        if (key.getKeyType() == KeyType.ArrowDown) { if (selected < rowCount - 1) selected++; return Result.stay(); }
        if (key.getKeyType() == KeyType.ArrowUp) { if (selected > 0) selected--; return Result.stay(); }
        if (key.getKeyType() == KeyType.PageDown) { selected = Math.min(Math.max(0, rowCount - 1), selected + visibleRows); return Result.stay(); }
        if (key.getKeyType() == KeyType.PageUp)   { selected = Math.max(0, selected - visibleRows); return Result.stay(); }
        if (key.getKeyType() == KeyType.Enter) {
            List<Row> rows = buildRows();
            if (selected >= 0 && selected < rows.size()) {
                return Result.push(detailFor(rows.get(selected).topic()));
            }
            return Result.stay();
        }
        if (key.getKeyType() == KeyType.Escape) return Result.pop();
        return Result.stay();
    }

    /** Builds the detail screen for a topic from its registered listeners (id + host). */
    private TopicDetailScreen detailFor(String topic) {
        Map<String, String> hostById = new java.util.HashMap<>();
        for (NodeRegistry.Node n : registry.nodes()) hostById.put(n.id, n.host);
        List<TopicDetailScreen.Listener> ls = new ArrayList<>();
        for (String id : registry.listenersOf(topic)) {
            ls.add(new TopicDetailScreen.Listener(id, hostById.get(id)));
        }
        return new TopicDetailScreen(topic, ls);
    }

    /** Union of ping- and stream-topics, sorted by message count (desc), silent topics alphabetically. */
    List<Row> buildRows() {
        Map<String, MessageStats.TopicAggregate> byTopic = new java.util.HashMap<>();
        for (MessageStats.TopicAggregate a : tracker.getStats().getTopicStats(Integer.MAX_VALUE)) {
            byTopic.put(a.topic(), a);
        }
        Set<String> all = new LinkedHashSet<>(registry.topicNames());
        all.addAll(byTopic.keySet());
        List<Row> rows = new ArrayList<>();
        for (String t : all) {
            MessageStats.TopicAggregate a = byTopic.get(t);
            int listeners = registry.listenersOf(t).size();
            if (a == null) rows.add(new Row(t, 0, 0, 0, 0, listeners));
            else rows.add(new Row(t, a.messages(), a.answers(), a.avgRtt(), a.timeouts(), listeners));
        }
        Comparator<Row> cmp = switch (sortKey) {
            case TOPIC     -> Comparator.comparing(Row::topic, String.CASE_INSENSITIVE_ORDER);
            case MESSAGES  -> Comparator.comparingLong(Row::messages);
            case ANSWERS   -> Comparator.comparingLong(Row::answers);
            case RTT       -> Comparator.comparingLong(Row::avgRtt);
            case TIMEOUTS  -> Comparator.comparingLong(Row::timeouts);
            case LISTENERS -> Comparator.comparingInt(Row::listeners);
        };
        if (sortDesc) cmp = cmp.reversed();
        rows.sort(cmp.thenComparing(Row::topic, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Override
    public void draw(TextGraphics g) {
        if (pendingRoster != null) {
            registry.seedFromStatus(pendingRoster, System.currentTimeMillis());
            pendingRoster = null;
        }
        tracker.markTimeouts(System.currentTimeMillis(), 2000);

        int width = g.getSize().getColumns();
        List<Row> rows = buildRows();
        rowCount = rows.size();
        if (selected >= rowCount) selected = Math.max(0, rowCount - 1);
        if (selected < 0) selected = 0;

        String arrow = sortDesc ? "▼" : "▲";
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 0, String.format("%-30s %8s %8s %8s %8s %9s",
                hdr("Topic", SortKey.TOPIC, arrow),
                hdr("Nachr.", SortKey.MESSAGES, arrow),
                hdr("Antw.", SortKey.ANSWERS, arrow),
                hdr("Ø-RTT", SortKey.RTT, arrow),
                hdr("Timeout", SortKey.TIMEOUTS, arrow),
                hdr("Listener", SortKey.LISTENERS, arrow)));

        int row = 2;
        int maxRows = Math.max(1, g.getSize().getRows() - 3);
        visibleRows = maxRows;
        // keep the selected row inside the window
        if (selected < scroll) scroll = selected;
        else if (selected >= scroll + visibleRows) scroll = selected - visibleRows + 1;
        int maxScroll = Math.max(0, rowCount - visibleRows);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;

        for (int i = scroll; i < rows.size() && row - 2 < maxRows; i++) {
            Row r = rows.get(i);
            String rtt = r.avgRtt() > 0 ? r.avgRtt() + "ms" : "—";
            TextColor rowColor = i == selected ? TextColor.ANSI.GREEN_BRIGHT
                    : (i % 2 == 0 ? TextColor.ANSI.DEFAULT : TextColor.ANSI.WHITE);
            g.setForegroundColor(rowColor);
            g.putString(2, row, String.format("%-30s %8d %8d %8s %8d ",
                    trunc(r.topic(), 30), r.messages(), r.answers(), rtt, r.timeouts()));
            // listener cell, flagged when firing into the void
            int lx = 2 + 30 + 1 + 8 + 1 + 8 + 1 + 8 + 1 + 8 + 1;
            if (r.firingIntoVoid()) {
                g.setForegroundColor(TextColor.ANSI.YELLOW);
                g.putString(lx, row, String.format("%9s", "⚠ 0"));
            } else {
                g.setForegroundColor(rowColor);
                g.putString(lx, row, String.format("%9d", r.listeners()));
            }
            row++;
        }
        if (rows.isEmpty()) {
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
            g.putString(2, row, "(keine Topics — Ping läuft …)");
        }

        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, g.getSize().getRows() - 1, trunc(statusLine(), width - 3));
    }

    private String statusLine() {
        String pos = rowCount == 0 ? "0/0"
                : (scroll + 1) + "–" + Math.min(rowCount, scroll + visibleRows) + "/" + rowCount;
        String base = "Intervall " + currentIntervalMs() / 1000 + "s" + (pinging ? "  pingt …" : "");
        return base + "   " + pos
                + "   [↑↓/PgUp/Dn] wählen   [enter] listener   [+/-] intervall   [r] ping   [esc] zurück   [q] quit";
    }

    private String hdr(String label, SortKey key, String arrow) {
        return sortKey == key ? label + arrow : label;
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        if (max <= 1) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

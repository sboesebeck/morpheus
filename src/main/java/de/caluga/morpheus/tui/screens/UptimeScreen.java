package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.core.latency.LatencyProbe;
import de.caluga.morpheus.core.latency.LatencyStats;
import de.caluga.morpheus.core.uptime.DriverHelloSource;
import de.caluga.morpheus.core.uptime.ReplicaSetWatcher;
import de.caluga.morpheus.core.uptime.UptimeTracker;
import de.caluga.morpheus.tui.Screen;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Uptime/failover view: per-node up/down timeline, availability table and a
 *  scrollable event log fed by the UptimeTracker (spec: 2026-08-04 uptime design). */
public class UptimeScreen implements Screen {

    private static final int[] MISSES = {1, 2, 3, 5, 10};
    private static final long WINDOW_MS = 10 * 60_000L;
    private static final DateTimeFormatter HMS =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final MorpheusContext ownedCtx;              // null im Test-Seam
    private final UptimeTracker.Snapshot fixed;          // non-null im Test-Seam
    private final LatencyStats.Snapshot fixedLat;
    private final String topic;

    UptimeTracker tracker;                               // überlebt Probe-Neustarts (Task 5)
    volatile LatencyProbe probe;                         // Task 5
    ReplicaSetWatcher watcher;                            // Task 5

    private long intervalMs = 1000;
    private int missIdx = 2;                             // MISSES[2] == 3
    private boolean rsWatch = true;
    private int eventScroll = 0;                          // 0 = neueste sichtbar
    private java.nio.file.Path csvDir = java.nio.file.Path.of(".");
    private String csvStatus;

    public UptimeScreen(MorpheusContext ctx) {
        this(ctx, LatencyProbe.Config.defaults().topic());
    }

    public UptimeScreen(MorpheusContext ctx, String topic) {
        this.ownedCtx = ctx;
        this.fixed = null;
        this.fixedLat = null;
        this.topic = topic;
    }

    /** Test seam: feste Snapshots, kein Backend. */
    UptimeScreen(UptimeTracker.Snapshot fixed, LatencyStats.Snapshot fixedLat) {
        this.ownedCtx = null;
        this.fixed = fixed;
        this.fixedLat = fixedLat;
        this.topic = LatencyProbe.Config.defaults().topic();
    }

    // ── test seams ──
    public long intervalMs() { return intervalMs; }
    public int missThreshold() { return MISSES[missIdx]; }
    public boolean rsWatchEnabled() { return rsWatch; }
    public int eventScroll() { return eventScroll; }
    public boolean isRunning() { return probe != null && probe.isRunning(); }
    void csvDir(java.nio.file.Path dir) { this.csvDir = dir; }
    String csvStatus() { return csvStatus; }

    @Override
    public void onClose() {
        stopRun();
        if (ownedCtx != null) ownedCtx.close();
    }

    void toggleRun() {
        if (isRunning()) { stopRun(); return; }
        if (ownedCtx == null || ownedCtx.getMessaging() == null) return;
        if (tracker == null) tracker = new UptimeTracker(missThreshold(), System::currentTimeMillis);
        probe = buildProbe();
        probe.start();
        if (rsWatch) startWatcher();
    }

    void stopRun() {
        if (probe != null) probe.stop();
        stopWatcher();
    }

    void restartIfRunning() {
        if (isRunning()) { probe.stop(); probe = buildProbe(); probe.start(); }
    }

    void writeCsv() {
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")
                .format(new java.util.Date());
        try {
            var files = de.caluga.morpheus.core.uptime.UptimeCsv.writeAll(csvDir, stamp, snapshot(), latSnapshot());
            csvStatus = "CSV: " + files.get(0).getFileName() + " (+2)";
        } catch (Exception e) {
            csvStatus = "CSV-Fehler: " + e.getMessage();
        }
    }

    /** Probe im festen INTERVAL-Mode; Stats überleben Neustarts (wie LatencyScreen.buildProbe),
     *  der Tracker sowieso (Feld). Der Outcome-Listener speist den Tracker. */
    private LatencyProbe buildProbe() {
        var d = LatencyProbe.Config.defaults();
        var cfg = new LatencyProbe.Config(topic, false, LatencyProbe.Mode.INTERVAL, intervalMs, d.rate(),
                d.rampStartRate(), d.rampStepRate(), d.rampStepMs(), d.rampAbortLossPct(),
                1, 0, d.timeoutMs(), d.bufferCapacity(), d.warmupMs());
        var messaging = ownedCtx.getMessaging();
        LatencyStats stats = probe != null ? probe.stats() : new LatencyStats(cfg.bufferCapacity());
        final LatencyProbe[] ref = new LatencyProbe[1];
        LatencyProbe p = new LatencyProbe(cfg, stats,
                m -> messaging.sendAndAwaitAsync(m, cfg.timeoutMs(), a -> ref[0].handleAnswer(a)),
                System::nanoTime, System::currentTimeMillis);
        ref[0] = p;
        p.setOutcomeListener(new de.caluga.morpheus.core.latency.PingOutcomeListener() {
            @Override public void onPingComplete(long seq, long sentTs, java.util.Set<String> answeredBy) {
                tracker.onPingComplete(seq, sentTs, answeredBy);
            }
            @Override public void onSendError(long sentTs) {
                tracker.recordSendError();
            }
        });
        return p;
    }

    private void startWatcher() {
        if (watcher != null || ownedCtx == null || ownedCtx.getMorphium() == null) return;
        watcher = new ReplicaSetWatcher(new DriverHelloSource(ownedCtx.getMorphium()), tracker, 1000);
        watcher.start();
    }

    private void stopWatcher() {
        if (watcher != null) { watcher.stop(); watcher = null; }
    }

    private UptimeTracker.Snapshot snapshot() {
        if (fixed != null) return fixed;
        if (tracker != null) return tracker.snapshot();
        return new UptimeTracker(missThreshold(), System::currentTimeMillis).snapshot();
    }

    private LatencyStats.Snapshot latSnapshot() {
        if (fixedLat != null) return fixedLat;
        if (probe != null) return probe.stats().snapshot();
        return new LatencyStats(1).snapshot();
    }

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) return Result.stay();
        if (key.getKeyType() == KeyType.Escape) return Result.pop();
        if (key.getKeyType() == KeyType.ArrowUp) { eventScroll = Math.min(eventScroll + 1, maxScroll()); return Result.stay(); }
        if (key.getKeyType() == KeyType.ArrowDown) { eventScroll = Math.max(0, eventScroll - 1); return Result.stay(); }
        Character c = key.getCharacter();
        if (c == null) return Result.stay();
        switch (c) {
            case 'q' -> { return Result.quit(); }
            case 's' -> toggleRun();
            case '+' -> { intervalMs += 250; restartIfRunning(); }
            case '-' -> { intervalMs = Math.max(250, intervalMs - 250); restartIfRunning(); }
            case 'n' -> {
                missIdx = (missIdx + 1) % MISSES.length;
                if (tracker != null) tracker.setMissThreshold(MISSES[missIdx]);
            }
            case 'r' -> { rsWatch = !rsWatch; onRsWatchToggled(); }
            case 'c' -> { if (tracker != null) tracker.reset(); }
            case 'w' -> writeCsv();
            default -> { }
        }
        return Result.stay();
    }

    void onRsWatchToggled() {
        if (!isRunning()) return;
        if (rsWatch) startWatcher(); else stopWatcher();
    }

    private int maxScroll() {
        return Math.max(0, snapshot().events().size() - 1);
    }

    @Override
    public void draw(TextGraphics g) {
        int width = g.getSize().getColumns();
        int height = g.getSize().getRows();
        if (width < 10 || height < 6) return;

        UptimeTracker.Snapshot snap = snapshot();
        drawHeader(g, width, snap);

        int keyHelpRow = height - 1;
        if (snap.nodes().isEmpty() && !isRunning()) {
            String hint = "press s to start monitoring";
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
            g.putString(Math.max(0, (width - hint.length()) / 2), height / 2, hint);
            drawKeyHelp(g, width, keyHelpRow);
            return;
        }

        int nodeCount = snap.nodes().size();
        int row = 1;
        // Timeline: Titelzeile (P-Marker) + Knotenzeilen + sender-Zeile
        int timelineRows = 1 + nodeCount + 1;
        int tableRows = 1 + nodeCount;
        int available = keyHelpRow - 1;
        if (timelineRows + tableRows + 1 <= available) {
            drawTimeline(g, row, width, snap);
            row += timelineRows;
        }
        if (row + tableRows <= available + 1) {
            drawTable(g, row, width, snap);
            row += tableRows;
        }
        drawEvents(g, row, keyHelpRow, width, snap);
        drawKeyHelp(g, width, keyHelpRow);
    }

    private void drawHeader(TextGraphics g, int width, UptimeTracker.Snapshot snap) {
        String conn = ownedCtx != null && ownedCtx.getMessaging() != null
                ? ownedCtx.getMessaging().getSenderId() : "-";
        double avail = snap.nodes().isEmpty() ? 100.0
                : snap.nodes().stream().mapToDouble(UptimeTracker.NodeStats::availabilityPct).average().orElse(100);
        String header = String.format(java.util.Locale.ROOT,
                "conn:%s  topic:%s  interval:%dms  misses:%d  %s  avail:%.1f%%",
                conn, topic, intervalMs, missThreshold(), isRunning() ? "RUNNING" : "STOPPED", avail);
        if (snap.sendErrors() > 0) header += "  sendErr:" + snap.sendErrors();
        if (csvStatus != null) header += "  " + csvStatus;
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 0, trunc(header, width - 3));
    }

    /** Eine Zeile pro Knoten: Fenster = letzte WINDOW_MS, Zell-Zustand per Mittelpunkt-Sampling
     *  über die StateChange-Liste; P-Marker (Primary-Wechsel) in der Titelzeile darüber. */
    private void drawTimeline(TextGraphics g, int top, int width, UptimeTracker.Snapshot snap) {
        int labelW = 9;
        int cells = Math.max(1, width - labelW - 3);
        long windowStart = snap.now() - WINDOW_MS;

        g.setForegroundColor(TextColor.ANSI.WHITE);
        g.putString(2, top, "Timeline");
        for (UptimeTracker.Event e : snap.events()) {
            if (e.type() != UptimeTracker.Event.Type.PRIMARY_CHANGE || e.ts() < windowStart) continue;
            int x = (int) ((e.ts() - windowStart) * cells / WINDOW_MS);
            g.putString(labelW + 2 + Math.min(cells - 1, x), top, "P");
        }

        int row = top + 1;
        for (UptimeTracker.NodeStats n : snap.nodes()) {
            List<UptimeTracker.StateChange> changes = snap.timelines().get(n.node());
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
            g.putString(2, row, trunc(n.node(), labelW - 1));
            for (int i = 0; i < cells; i++) {
                long cellTs = windowStart + (long) ((i + 0.5) * WINDOW_MS / cells);
                Character ch; TextColor color;
                if (cellTs < n.learnedTs()) { ch = '░'; color = TextColor.ANSI.BLACK_BRIGHT; }
                else if (stateAt(changes, cellTs) == UptimeTracker.State.DOWN) { ch = '█'; color = TextColor.ANSI.RED; }
                else { ch = '█'; color = TextColor.ANSI.GREEN; }
                g.setForegroundColor(color);
                g.putString(labelW + 2 + i, row, String.valueOf(ch));
            }
            row++;
        }
        // sender row: Send-Error-Events rot markieren
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, row, "sender");
        for (int i = 0; i < cells; i++) {
            g.setForegroundColor(TextColor.ANSI.GREEN);
            g.putString(labelW + 2 + i, row, "█");
        }
        for (UptimeTracker.Event e : snap.events()) {
            if (e.type() != UptimeTracker.Event.Type.SEND_ERROR || e.ts() < windowStart) continue;
            int x = (int) ((e.ts() - windowStart) * cells / WINDOW_MS);
            g.setForegroundColor(TextColor.ANSI.RED);
            g.putString(labelW + 2 + Math.min(cells - 1, x), row, "█");
        }
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
    }

    private static UptimeTracker.State stateAt(List<UptimeTracker.StateChange> changes, long ts) {
        UptimeTracker.State st = UptimeTracker.State.UP;
        if (changes == null) return st;
        for (UptimeTracker.StateChange c : changes) {
            if (c.ts() > ts) break;
            st = c.state();
        }
        return st;
    }

    private void drawTable(TextGraphics g, int top, int width, UptimeTracker.Snapshot snap) {
        g.setForegroundColor(TextColor.ANSI.CYAN);
        String header = String.format("%-24s %-6s %-9s %7s %9s %9s %9s %8s",
                "Node", "Status", "seit", "avail%", "Ausfälle", "längster", "⌀Ausfall", "lastRTT");
        g.putString(2, top, trunc(header, width - 3));
        LatencyStats.Snapshot lat = latSnapshot();
        int row = top + 1;
        for (UptimeTracker.NodeStats n : snap.nodes()) {
            String lastRtt = "-";
            for (LatencyStats.ResponderStats r : lat.responders()) {
                if (r.key().equals(n.node())) { lastRtt = String.format(java.util.Locale.ROOT, "%.1fms", r.lastMs()); break; }
            }
            g.setForegroundColor(n.state() == UptimeTracker.State.DOWN ? TextColor.ANSI.RED : TextColor.ANSI.GREEN);
            g.putString(2, row, "●");
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
            String line = String.format(java.util.Locale.ROOT, "%-22s %-6s %-9s %7.1f %9d %9s %9s %8s",
                    trunc(n.node(), 22), n.state(), HMS.format(Instant.ofEpochMilli(n.stateSinceTs())),
                    n.availabilityPct(), n.outages(),
                    n.outages() == 0 ? "-" : fmtDur(n.longestOutageMs()),
                    n.outages() == 0 ? "-" : fmtDur(n.avgOutageMs()), lastRtt);
            g.putString(4, row, trunc(line, width - 5));
            row++;
        }
    }

    private void drawEvents(TextGraphics g, int top, int keyHelpRow, int width, UptimeTracker.Snapshot snap) {
        int rows = keyHelpRow - top;
        if (rows < 2) return;
        g.setForegroundColor(TextColor.ANSI.WHITE);
        g.putString(2, top, "Events");
        List<UptimeTracker.Event> events = snap.events();
        int visible = rows - 1;
        int end = Math.max(0, events.size() - eventScroll);
        int start = Math.max(0, end - visible);
        int row = top + 1;
        for (int i = start; i < end; i++) {
            UptimeTracker.Event e = events.get(i);
            g.setForegroundColor(switch (e.type()) {
                case DOWN, SEND_ERROR, RS_UNKNOWN -> TextColor.ANSI.RED;
                case UP, RS_RECOVERED -> TextColor.ANSI.GREEN;
                case PRIMARY_CHANGE -> TextColor.ANSI.WHITE_BRIGHT;
                case LEARNED -> TextColor.ANSI.CYAN;
            });
            String line = String.format("%s  %-14s %-24s %s",
                    HMS.format(Instant.ofEpochMilli(e.ts())), e.type(), trunc(e.node(), 24), e.detail());
            g.putString(2, row, trunc(line, width - 3));
            row++;
        }
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
    }

    private void drawKeyHelp(TextGraphics g, int width, int row) {
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, row, trunc(
                "s start/stop  +/- intervall  n misses  r RS-watch  w CSV  ↑↓ events  c reset  q zurück",
                width - 3));
    }

    static String fmtDur(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60_000) return String.format(java.util.Locale.ROOT, "%.1fs", ms / 1000.0);
        return (ms / 60_000) + "m" + (ms % 60_000) / 1000 + "s";
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        if (max <= 1) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

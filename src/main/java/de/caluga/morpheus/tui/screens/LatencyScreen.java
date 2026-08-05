package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.core.latency.LatencyCsv;
import de.caluga.morpheus.core.latency.LatencyProbe;
import de.caluga.morpheus.core.latency.LatencySample;
import de.caluga.morpheus.core.latency.LatencyStats;
import de.caluga.morpheus.tui.Screen;
import de.caluga.morpheus.tui.widget.BrailleCanvas;
import de.caluga.morpheus.tui.widget.TopicPalette;
import de.caluga.morphium.messaging.MorphiumMessaging;
import de.caluga.morphium.messaging.Msg;

import java.util.List;
import java.util.function.Consumer;

/** Live latency view: braille RTT time series (or histogram), per-responder
 *  percentile table, start/stop + load parameters via keys. */
public class LatencyScreen implements Screen {

    private static final int[] PAYLOADS = {0, 1024, 10240, 102400};
    private static final int[] THREADS = {1, 2, 4, 8};

    private final MorpheusContext ownedCtx;      // null in test seam
    private final LatencyStats.Snapshot fixed;   // non-null in test seam
    private final String initialTopic;
    private final boolean statusMode;
    private volatile LatencyProbe probe;          // recreated on start/settings change; UI-thread only —
                                                   // the async answer callback (see wireSender) reads the
                                                   // per-instance probeRef cell captured in buildProbe(),
                                                   // never this field, so an old probe's in-flight answers
                                                   // can't land on a newer one after a restart

    private LatencyProbe.Mode mode = LatencyProbe.Mode.INTERVAL;
    private long intervalMs = 1000;
    private double rate = 10;
    private double rampStep = 50;
    private int payloadIdx = 0;
    private int threadIdx = 0;
    private boolean histogram = false;
    private java.nio.file.Path exportDir = java.nio.file.Path.of(".");
    private String exportNote;
    private long exportNoteAt;

    public LatencyScreen(MorpheusContext ctx) {
        this(ctx, LatencyProbe.Config.defaults().topic(), false);
    }

    public LatencyScreen(MorpheusContext ctx, String topic, boolean statusMode) {
        this.ownedCtx = ctx;
        this.fixed = null;
        this.initialTopic = topic;
        this.statusMode = statusMode;
    }

    /** Test seam: render a fixed snapshot, no backend. */
    LatencyScreen(LatencyStats.Snapshot fixed) {
        this.ownedCtx = null;
        this.fixed = fixed;
        this.initialTopic = LatencyProbe.Config.defaults().topic();
        this.statusMode = false;
    }

    boolean histogramVisible() { return histogram; }
    boolean isRunning() { return probe != null && probe.isRunning(); }
    LatencyProbe.Mode mode() { return mode; }
    int payloadBytes() { return PAYLOADS[payloadIdx]; }
    int senderThreads() { return THREADS[threadIdx]; }
    void setExportDir(java.nio.file.Path dir) { exportDir = dir; }   // test seam
    String exportNote() { return exportNote; }

    @Override
    public void onClose() {
        if (probe != null) probe.stop();
        if (ownedCtx != null) ownedCtx.close();
    }

    private void toggleRun() {
        if (isRunning()) { probe.stop(); return; }
        if (ownedCtx == null || ownedCtx.getMessaging() == null) return;
        probe = buildProbe();
        probe.start();
    }

    LatencyProbe.Config currentConfig() {
        var d = LatencyProbe.Config.defaults();
        String topic = initialTopic;
        if (statusMode && ownedCtx != null && ownedCtx.getMessaging() != null) {
            String resolved = ownedCtx.getMessaging().getStatusInfoListenerName();
            if (resolved != null) topic = resolved;
        }
        return new LatencyProbe.Config(topic, statusMode, mode, intervalMs, rate,
                50, rampStep, 10_000, 50.0, senderThreads(), payloadBytes(),
                d.timeoutMs(), d.bufferCapacity(), d.warmupMs());
    }

    /** Send strategy: sendAndAwaitAsync's per-message callback. A swap to sendMessage + own topic
     *  listener was considered and rejected — it would only matter if messaging itself leaked
     *  waiters, and a latency tool measuring a fundamentally broken bus is moot anyway.
     *  Callback semantics differ per messaging implementation: SingleCollectionMessaging keeps the
     *  callback registered until timeout, so every answer (multiple pongs, --status with N nodes)
     *  is delivered; MultiCollectionMessaging on morphium <= 6.2.x deregisters after the FIRST
     *  answer per ping, so only the fastest responder is sampled there (fixed in morphium 6.3.0).
     *  Binds to the specific probe instance via probeRef (not the outer mutable field) so in-flight
     *  answers for pings sent by an OLD probe are never misdelivered to a NEW one after a restart. */
    private Consumer<Msg> wireSender(MorphiumMessaging messaging, LatencyProbe.Config cfg, LatencyProbe[] probeRef) {
        return m -> messaging.sendAndAwaitAsync(m, cfg.timeoutMs(), a -> probeRef[0].handleAnswer(a));
    }

    private LatencyProbe buildProbe() {
        var cfg = currentConfig();
        var messaging = ownedCtx.getMessaging();
        LatencyStats stats = probe != null ? probe.stats() : new LatencyStats(cfg.bufferCapacity());
        final LatencyProbe[] ref = new LatencyProbe[1];
        LatencyProbe p = new LatencyProbe(cfg, stats, wireSender(messaging, cfg, ref), System::nanoTime, System::currentTimeMillis);
        ref[0] = p;
        return p;
    }

    private void restartIfRunning() {
        if (isRunning()) { probe.stop(); probe = buildProbe(); probe.start(); }
    }

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) return Result.stay();
        if (key.getKeyType() == KeyType.Escape) return Result.pop();
        Character c = key.getCharacter();
        if (c == null) return Result.stay();
        switch (c) {
            case 'q' -> { return Result.quit(); }
            case 's' -> toggleRun();
            case 'h' -> histogram = !histogram;
            case 'e' -> exportCsv();
            case 'm' -> { mode = LatencyProbe.Mode.values()[(mode.ordinal() + 1) % LatencyProbe.Mode.values().length]; restartIfRunning(); }
            case 'P' -> { if (payloadIdx < PAYLOADS.length - 1) { payloadIdx++; restartIfRunning(); } }
            case 'p' -> { if (payloadIdx > 0) { payloadIdx--; restartIfRunning(); } }
            case 'T' -> { if (threadIdx < THREADS.length - 1) { threadIdx++; restartIfRunning(); } }
            case 't' -> { if (threadIdx > 0) { threadIdx--; restartIfRunning(); } }
            case '+' -> { adjust(+1); restartIfRunning(); }
            case '-' -> { adjust(-1); restartIfRunning(); }
            default -> { }
        }
        return Result.stay();
    }

    private void adjust(int dir) {
        switch (mode) {
            case INTERVAL -> intervalMs = Math.max(250, intervalMs + dir * 250L);
            case FIXED_RATE -> rate = Math.max(1, rate + dir * 10);
            case RAMP -> rampStep = Math.max(10, rampStep + dir * 10);
        }
    }

    /** 'e': dump the raw sample series (not the aggregated table) to a timestamped CSV
     *  in the working directory, so a run can be re-plotted/analyzed offline. */
    private void exportCsv() {
        List<LatencySample> samples = snapshot().samples();
        if (samples.isEmpty()) {
            note("nothing to export - no samples yet");
            return;
        }
        String name = "latency-samples-" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
        try {
            java.nio.file.Path written = LatencyCsv.writeSamples(exportDir.resolve(name), samples);
            note("exported " + samples.size() + " samples to " + written);
        } catch (Exception e) {
            note("export failed: " + e.getMessage());
        }
    }

    private void note(String text) {
        exportNote = text;
        exportNoteAt = System.currentTimeMillis();
    }

    private LatencyStats.Snapshot snapshot() {
        if (fixed != null) return fixed;
        if (probe != null) return probe.stats().snapshot();
        return new LatencyStats(1).snapshot();
    }

    @Override
    public void draw(TextGraphics g) {
        int width = g.getSize().getColumns();
        int height = g.getSize().getRows();
        if (width < 10 || height < 6) return;   // nothing sensible to draw in a tiny terminal

        LatencyStats.Snapshot snap = snapshot();
        drawHeader(g, width, snap);

        int keyHelpRow = height - 1;
        int graphTop = 1;
        int middleRows = Math.max(1, keyHelpRow - graphTop);   // rows shared by graph + table
        boolean empty = snap.samples().isEmpty() && !isRunning();

        int responderCount = snap.responders().size();
        int maxTableDataRows = 0;
        int tableTotalRows = 0;
        if (!empty) {
            maxTableDataRows = Math.max(0, Math.min(responderCount, middleRows - 2));   // leave >=2 rows for the graph
            tableTotalRows = 1 + maxTableDataRows;                                      // header + data rows
        }
        int graphRows = Math.max(1, middleRows - tableTotalRows);
        int tableTop = graphTop + graphRows;

        if (empty) {
            String hint = "press s to start pinging";
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
            g.putString(Math.max(0, (width - hint.length()) / 2), Math.max(graphTop, height / 2), hint);
        } else if (histogram) {
            drawHistogram(g, 2, graphTop, width - 3, graphRows, snap);
        } else {
            drawGraph(g, graphTop, graphRows, width, snap);
        }

        if (!empty) drawTable(g, 2, tableTop, width - 3, maxTableDataRows, snap);

        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        String help = exportNote != null && System.currentTimeMillis() - exportNoteAt < 5000
                ? exportNote
                : String.format("s start/stop  m mode  +/- rate  p/P payload:%s  t/T threads:%d  "
                        + "h histogram  e export csv  q back", fmtBytes(payloadBytes()), senderThreads());
        g.putString(2, keyHelpRow, trunc(help, width - 3));
    }

    private static String fmtBytes(int bytes) {
        return bytes >= 1024 ? (bytes / 1024) + "k" : String.valueOf(bytes);
    }

    private void drawHeader(TextGraphics g, int width, LatencyStats.Snapshot snap) {
        String conn = ownedCtx != null && ownedCtx.getMessaging() != null
                ? ownedCtx.getMessaging().getSenderId() : "-";
        String topic = currentConfig().topic();
        String status = probe != null && probe.rampAborted() ? "RAMP-ABORTED" : (isRunning() ? "RUNNING" : "STOPPED");
        double target = probe != null ? probe.targetRate() : 0.0;
        double actual = probe != null ? probe.actualRate() : 0.0;
        String header = String.format("conn:%s  topic:%s  mode:%s  rate(target/actual):%.1f/%.1f  %s",
                conn, topic, mode, target, actual, status);
        if (snap.sendErrors() > 0) header += "  sendErr:" + snap.sendErrors();
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 0, trunc(header, width - 3));
    }

    /** Braille RTT time series: one subpixel column per sample (last canvas.width() samples), colour
     *  per responder, connected within a responder's own run of samples; red x marks timeouts; a white
     *  dotted vertical line marks each ramp step. */
    private void drawGraph(TextGraphics g, int top, int rows, int width, LatencyStats.Snapshot snap) {
        int originX = 4;
        BrailleCanvas canvas = new BrailleCanvas(Math.max(1, width - 4), Math.max(1, rows));
        List<LatencySample> samples = snap.samples();
        int n = Math.min(samples.size(), canvas.width());
        List<LatencySample> window = n == 0 ? List.of() : samples.subList(samples.size() - n, samples.size());

        double maxRtt = 1.0;
        for (LatencySample s : window) maxRtt = Math.max(maxRtt, s.rttMs());

        int prevX = -1, prevY = -1;
        String prevKey = null;
        for (int i = 0; i < window.size(); i++) {
            LatencySample s = window.get(i);
            int x = i;
            int y = (int) Math.round((canvas.height() - 1) * (1.0 - Math.min(1.0, s.rttMs() / maxRtt)));
            TextColor color = TopicPalette.colorFor(s.responderKey());
            if (prevX >= 0 && s.responderKey().equals(prevKey)) {
                canvas.line(prevX, prevY, x, y, color);
            } else {
                canvas.plot(x, y, color);
            }
            prevX = x; prevY = y; prevKey = s.responderKey();
        }

        if (probe != null && !window.isEmpty()) {
            for (Long rampSeq : probe.rampStepSeqs()) {
                // Same >=-fallback as the timeout markers below: a boundary seq that fell in a gap
                // (evicted/never-sampled) still lands on the first sample at/after it, or the last
                // column if the step is newer than every sample currently in the window.
                int si = -1;
                for (int i = 0; i < window.size(); i++) {
                    if (window.get(i).seq() >= rampSeq) { si = i; break; }
                }
                if (si < 0) si = window.size() - 1;
                for (int sy = 0; sy < canvas.height(); sy += 2) canvas.plot(si, sy, TextColor.ANSI.WHITE);
            }
        }

        canvas.render(g, originX, top);

        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(0, top, trunc(fmt1(maxRtt), 4));
        g.putString(0, top + rows - 1, "0");

        if (!window.isEmpty()) {
            long minSeq = Long.MAX_VALUE, maxSeq = Long.MIN_VALUE;
            for (LatencySample s : window) { minSeq = Math.min(minSeq, s.seq()); maxSeq = Math.max(maxSeq, s.seq()); }
            g.setForegroundColor(TextColor.ANSI.RED);
            for (Long ts : snap.timeoutSeqs()) {
                if (ts < minSeq || ts > maxSeq) continue;
                int sx = -1;
                for (int i = 0; i < window.size(); i++) {
                    if (window.get(i).seq() >= ts) { sx = i; break; }
                }
                if (sx < 0) sx = window.size() - 1;
                g.putString(originX + sx / 2, top, "×");
            }
        }
    }

    /** One bar row per RTT bucket, proportional to the widest bucket. */
    private void drawHistogram(TextGraphics g, int left, int top, int width, int rows, LatencyStats.Snapshot snap) {
        long[] hist = snap.histogram();
        long maxCount = 1;
        for (long c : hist) maxCount = Math.max(maxCount, c);
        int barWidth = Math.max(1, width - 18);
        int shown = Math.min(hist.length, Math.max(0, rows));
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        for (int i = 0; i < shown; i++) {
            long count = hist[i];
            int barLen = (int) Math.round(barWidth * (count / (double) maxCount));
            String line = String.format("%-8s %6d %s", bucketLabel(i), count, "█".repeat(Math.max(0, barLen)));
            g.putString(left, top + i, trunc(line, width));
        }
    }

    private String bucketLabel(int i) {
        double[] b = LatencyStats.BUCKET_BOUNDS;
        if (i < b.length) return "≤" + fmtBound(b[i]) + "ms";
        return ">" + fmtBound(b[b.length - 1]) + "ms";
    }

    private String fmtBound(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private void drawTable(TextGraphics g, int left, int top, int width, int maxRows, LatencyStats.Snapshot snap) {
        g.setForegroundColor(TextColor.ANSI.CYAN);
        String header = String.format("%-24s %6s %6s %7s %7s %7s %7s %7s %7s %6s %7s %7s",
                "Responder", "cnt", "loss%", "last", "min", "avg", "p50", "p90", "p99", "jit", "out≈", "ret≈");
        g.putString(left, top, trunc(header, width));

        int row = top + 1;
        int shown = 0;
        for (LatencyStats.ResponderStats r : snap.responders()) {
            if (shown >= maxRows) break;
            shown++;
            g.setForegroundColor(TopicPalette.colorFor(r.key()));
            g.putString(left, row, "●");
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
            String line = String.format("%-22s %6d %6.1f %7.2f %7.2f %7.2f %7.2f %7.2f %7.2f %6.2f %7s %7s",
                    trunc(r.key(), 22), r.count(), r.lossPct(), r.lastMs(), r.minMs(), r.avgMs(),
                    r.p50(), r.p90(), r.p99(), r.jitterMs(), fmtOrUnknown(r.outboundMs()), fmtOrUnknown(r.returnMs()));
            g.putString(left + 2, row, trunc(line, Math.max(0, width - 2)));
            row++;
        }
    }

    private String fmtOrUnknown(Double v) {
        if (v == null || v < 0) return "?";
        return fmt1(v);
    }

    private String fmt1(double v) {
        return String.format("%.1f", v);
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        if (max <= 1) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

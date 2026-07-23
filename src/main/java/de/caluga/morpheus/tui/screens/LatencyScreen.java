package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import de.caluga.morpheus.cli.MorpheusContext;
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
    private volatile LatencyProbe probe;          // recreated on start/settings change; read from the
                                                   // async answer callback thread (see wireSender), written on the UI thread

    private LatencyProbe.Mode mode = LatencyProbe.Mode.INTERVAL;
    private long intervalMs = 1000;
    private double rate = 10;
    private double rampStep = 50;
    private int payloadIdx = 0;
    private int threadIdx = 0;
    private boolean histogram = false;

    public LatencyScreen(MorpheusContext ctx) {
        this.ownedCtx = ctx;
        this.fixed = null;
    }

    /** Test seam: render a fixed snapshot, no backend. */
    LatencyScreen(LatencyStats.Snapshot fixed) {
        this.ownedCtx = null;
        this.fixed = fixed;
    }

    boolean histogramVisible() { return histogram; }
    boolean isRunning() { return probe != null && probe.isRunning(); }
    LatencyProbe.Mode mode() { return mode; }
    int payloadBytes() { return PAYLOADS[payloadIdx]; }
    int senderThreads() { return THREADS[threadIdx]; }

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

    private LatencyProbe.Config currentConfig() {
        var d = LatencyProbe.Config.defaults();
        return new LatencyProbe.Config(d.topic(), false, mode, intervalMs, rate,
                50, rampStep, 10_000, 50.0, senderThreads(), payloadBytes(),
                d.timeoutMs(), d.bufferCapacity());
    }

    /** Encapsulates the send strategy so a later task (poppydb wiring, Task 8) can swap it in one
     *  place. Currently uses sendAndAwaitAsync's per-message callback; if that turns out to block or
     *  leak waiters, replace the body with messaging.sendMessage(msg) + a topic listener that forwards
     *  answers (getInAnswerTo() != null) to probe.handleAnswer and returns null. */
    private Consumer<Msg> wireSender(MorphiumMessaging messaging, LatencyProbe.Config cfg) {
        return m -> messaging.sendAndAwaitAsync(m, cfg.timeoutMs(), a -> probe.handleAnswer(a));
    }

    private LatencyProbe buildProbe() {
        var cfg = currentConfig();
        var messaging = ownedCtx.getMessaging();
        LatencyStats stats = probe != null ? probe.stats() : new LatencyStats(cfg.bufferCapacity());
        return new LatencyProbe(cfg, stats, wireSender(messaging, cfg), System::nanoTime, System::currentTimeMillis);
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
            case 'm' -> { mode = LatencyProbe.Mode.values()[(mode.ordinal() + 1) % 3]; restartIfRunning(); }
            case 'P' -> { payloadIdx = (payloadIdx + 1) % PAYLOADS.length; restartIfRunning(); }
            case 'T' -> { threadIdx = (threadIdx + 1) % THREADS.length; restartIfRunning(); }
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
        g.putString(2, keyHelpRow, trunc(
                "s start/stop  m mode  +/- rate  P payload  T threads  h histogram  q back", width - 3));
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
                for (int i = 0; i < window.size(); i++) {
                    if (window.get(i).seq() == rampSeq) {
                        for (int sy = 0; sy < canvas.height(); sy += 2) canvas.plot(i, sy, TextColor.ANSI.WHITE);
                        break;
                    }
                }
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

package de.caluga.morpheus.cli;

import de.caluga.morpheus.core.latency.LatencyProbe;
import de.caluga.morpheus.core.latency.LatencyStats;
import de.caluga.morpheus.tui.MorpheusTui;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Locale;
import java.util.concurrent.Callable;

@Command(name = "latency",
         description = "Message-bus latency monitor / load test (TUI; --headless for scripting).",
         mixinStandardHelpOptions = true)
public class LatencyCommand implements Callable<Integer> {

    @ParentCommand RootCommand parent;

    @Option(names = {"--headless"}, description = "Run without TUI, print a summary at the end.")
    boolean headless;
    @Option(names = {"--topic"}, defaultValue = "morpheus_latency",
            description = "Ping topic (default: ${DEFAULT-VALUE}).")
    String topic;
    @Option(names = {"--status"}, description = "Ping the Morphium status-info listener instead (real app nodes).")
    boolean status;
    @Option(names = {"--interval"}, description = "Ping interval in ms (default mode, default: 1000).")
    Long intervalMs;
    @Option(names = {"--rate"}, description = "Fixed send rate in msgs/s (enables load mode).")
    Double rate;
    @Option(names = {"--ramp"}, description = "Ramp mode start:step:stepSecs, e.g. 50:50:10.")
    String ramp;
    @Option(names = {"--threads"}, defaultValue = "1", description = "Sender threads (default: ${DEFAULT-VALUE}).")
    int threads;
    @Option(names = {"--payload"}, defaultValue = "0", description = "Ping padding bytes (default: ${DEFAULT-VALUE}).")
    int payload;
    @Option(names = {"--timeout"}, defaultValue = "5000", description = "Ping timeout ms (default: ${DEFAULT-VALUE}).")
    long timeoutMs;
    @Option(names = {"--duration"}, defaultValue = "60", description = "Headless run duration in seconds (default: ${DEFAULT-VALUE}).")
    int durationSecs;
    @Option(names = {"--json"}, description = "Print summary as JSON.")
    boolean json;
    @Option(names = {"--csv"}, description = "Print summary as CSV.")
    boolean csv;
    @Option(names = {"--graphite"}, description = "Graphite host[:port] to push per-responder metrics to.")
    String graphite;
    @Option(names = {"--prefix"}, defaultValue = "morpheus", description = "Graphite metric prefix (default: ${DEFAULT-VALUE}).")
    String prefix;
    @Option(names = {"--max-loss"}, description = "Exit non-zero when total loss %% exceeds this.")
    Double maxLoss;

    @Override
    public Integer call() throws Exception {
        if (!headless) {
            MorpheusTui.launchView(parent.context(), "latency");
            return 0;
        }
        MorpheusContext ctx = parent.context();
        ctx.connect();
        var cfg = buildConfig(ctx);
        LatencyStats stats = new LatencyStats(cfg.bufferCapacity());
        final LatencyProbe[] ref = new LatencyProbe[1];
        LatencyProbe probe = new LatencyProbe(cfg, stats,
                m -> ctx.getMessaging().sendAndAwaitAsync(m, cfg.timeoutMs(), a -> ref[0].handleAnswer(a)),
                System::nanoTime, System::currentTimeMillis);
        ref[0] = probe;
        probe.start();
        for (int i = 0; i < durationSecs && !probe.rampAborted(); i++) Thread.sleep(1000);
        probe.stop();
        Thread.sleep(Math.min(cfg.timeoutMs(), 2000)); // let stragglers arrive
        probe.sweep();
        var snap = stats.snapshot();
        if (json) System.out.println(reportJson(snap));
        else if (csv) System.out.println(reportCsv(snap));
        else printThemed(ctx, snap, probe);
        if (graphite != null) pushGraphite(snap);
        return maxLoss != null && snap.lossPct() > maxLoss ? 2 : 0;
    }

    LatencyProbe.Config buildConfig(MorpheusContext ctx) {
        var d = LatencyProbe.Config.defaults();
        LatencyProbe.Mode mode = ramp != null ? LatencyProbe.Mode.RAMP
                : rate != null ? LatencyProbe.Mode.FIXED_RATE : LatencyProbe.Mode.INTERVAL;
        double rampStart = d.rampStartRate(), rampStep = d.rampStepRate();
        long rampStepMs = d.rampStepMs();
        if (ramp != null) {
            String[] parts = ramp.split(":");
            rampStart = Double.parseDouble(parts[0]);
            rampStep = Double.parseDouble(parts[1]);
            rampStepMs = Long.parseLong(parts[2]) * 1000L;
        }
        String t = status ? ctx.getMessaging().getStatusInfoListenerName() : topic;
        return new LatencyProbe.Config(t, status, mode,
                intervalMs != null ? intervalMs : d.intervalMs(),
                rate != null ? rate : d.rate(),
                rampStart, rampStep, rampStepMs, d.rampAbortLossPct(),
                threads, payload, timeoutMs, d.bufferCapacity());
    }

    static String reportJson(LatencyStats.Snapshot s) {
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.ROOT,
                "{\"pingsSent\":%d,\"completed\":%d,\"timeouts\":%d,\"sendErrors\":%d,\"lossPct\":%.2f,\"responders\":[",
                s.pingsSent(), s.completed(), s.timeouts(), s.sendErrors(), s.lossPct()));
        for (int i = 0; i < s.responders().size(); i++) {
            var r = s.responders().get(i);
            if (i > 0) b.append(',');
            b.append(String.format(Locale.ROOT,
                    "{\"key\":\"%s\",\"count\":%d,\"lossPct\":%.2f,\"lastMs\":%.3f,\"minMs\":%.3f,"
                    + "\"avgMs\":%.3f,\"maxMs\":%.3f,\"p50\":%.3f,\"p90\":%.3f,\"p99\":%.3f,\"jitterMs\":%.3f,"
                    + "\"outboundMs\":%s,\"returnMs\":%s}",
                    r.key(), r.count(), r.lossPct(), r.lastMs(), r.minMs(), r.avgMs(), r.maxMs(),
                    r.p50(), r.p90(), r.p99(), r.jitterMs(), num(r.outboundMs()), num(r.returnMs())));
        }
        return b.append("]}").toString();
    }

    static String reportCsv(LatencyStats.Snapshot s) {
        StringBuilder b = new StringBuilder("key,count,lossPct,lastMs,minMs,avgMs,maxMs,p50,p90,p99,jitterMs,outboundMs,returnMs\n");
        for (var r : s.responders()) {
            b.append(String.format(Locale.ROOT, "%s,%d,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%.3f,%s,%s\n",
                    r.key(), r.count(), r.lossPct(), r.lastMs(), r.minMs(), r.avgMs(), r.maxMs(),
                    r.p50(), r.p90(), r.p99(), r.jitterMs(), csvNum(r.outboundMs()), csvNum(r.returnMs())));
        }
        return b.toString();
    }

    private static String num(Double d) { return d == null ? "null" : String.format(Locale.ROOT, "%.3f", d); }
    private static String csvNum(Double d) { return d == null ? "" : String.format(Locale.ROOT, "%.3f", d); }

    private void printThemed(MorpheusContext ctx, LatencyStats.Snapshot s, LatencyProbe probe) {
        ctx.pr("[header1]latency summary[r]  pings: " + s.pingsSent() + "  completed: " + s.completed()
                + "  timeouts: " + s.timeouts() + "  loss: " + String.format(Locale.ROOT, "%.2f%%", s.lossPct())
                + (probe.rampAborted() ? "  [warning]RAMP ABORTED[r]" : ""));
        ctx.pr("[c3]" + ctx.getColumn("Responder", 30) + " | " + ctx.getColumn("cnt", 6) + " | "
                + ctx.getColumn("loss%", 6) + " | " + ctx.getColumn("avg", 7) + " | " + ctx.getColumn("p50", 7)
                + " | " + ctx.getColumn("p90", 7) + " | " + ctx.getColumn("p99", 7) + " | " + ctx.getColumn("jit", 6));
        for (var r : s.responders()) {
            ctx.pr(ctx.getColumn(r.key(), 30) + " | " + ctx.getColumn(String.valueOf(r.count()), 6) + " | "
                    + ctx.getColumn(String.format(Locale.ROOT, "%.1f", r.lossPct()), 6) + " | "
                    + ctx.getColumn(String.format(Locale.ROOT, "%.2f", r.avgMs()), 7) + " | "
                    + ctx.getColumn(String.format(Locale.ROOT, "%.2f", r.p50()), 7) + " | "
                    + ctx.getColumn(String.format(Locale.ROOT, "%.2f", r.p90()), 7) + " | "
                    + ctx.getColumn(String.format(Locale.ROOT, "%.2f", r.p99()), 7) + " | "
                    + ctx.getColumn(String.format(Locale.ROOT, "%.2f", r.jitterMs()), 6));
        }
    }

    private void pushGraphite(LatencyStats.Snapshot s) {
        // Same socket mechanism as PingCommand.sendToGraphite (host[:port], plaintext protocol).
        // Metrics per responder (key sanitized: [^A-Za-z0-9_-] -> '_'):
        //   <prefix>.latency.<key>.avg|p50|p90|p99|loss
        String graphiteHost = graphite;
        int graphitePort = 8125;
        if (graphite.contains(":")) {
            String[] parts = graphite.split(":", 2);
            graphiteHost = parts[0];
            graphitePort = Integer.parseInt(parts[1]);
        }
        Socket graphiteSocket = null;
        PrintWriter graphiteOut = null;
        try {
            graphiteSocket = new Socket(graphiteHost, graphitePort);
            graphiteOut = new PrintWriter(new OutputStreamWriter(graphiteSocket.getOutputStream()));
            long ts = System.currentTimeMillis() / 1000;
            for (var r : s.responders()) {
                String key = r.key().replaceAll("[^A-Za-z0-9_-]", "_");
                String base = prefix + ".latency." + key + ".";
                graphiteOut.println(base + "avg " + r.avgMs() + " " + ts);
                graphiteOut.println(base + "p50 " + r.p50() + " " + ts);
                graphiteOut.println(base + "p90 " + r.p90() + " " + ts);
                graphiteOut.println(base + "p99 " + r.p99() + " " + ts);
                graphiteOut.println(base + "loss " + r.lossPct() + " " + ts);
            }
            graphiteOut.flush();
        } catch (Exception e) {
            System.err.println("[latency] graphite push failed: " + e.getMessage());
        } finally {
            if (graphiteSocket != null) {
                try { graphiteSocket.close(); } catch (Exception e) { /* ignore */ }
            }
        }
    }
}

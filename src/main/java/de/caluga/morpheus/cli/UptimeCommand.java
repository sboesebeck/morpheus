package de.caluga.morpheus.cli;

import de.caluga.morpheus.core.latency.LatencyProbe;
import de.caluga.morpheus.core.latency.LatencyStats;
import de.caluga.morpheus.core.latency.PingOutcomeListener;
import de.caluga.morpheus.core.uptime.DriverHelloSource;
import de.caluga.morpheus.core.uptime.ReplicaSetWatcher;
import de.caluga.morpheus.core.uptime.UptimeCsv;
import de.caluga.morpheus.core.uptime.UptimeTracker;
import de.caluga.morpheus.tui.MorpheusTui;
import de.caluga.morpheus.tui.screens.UptimeScreen;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(name = "uptime",
         description = "Uptime/failover monitor over pong responders (TUI; --headless for scripting).",
         mixinStandardHelpOptions = true)
public class UptimeCommand implements Callable<Integer> {

    @ParentCommand RootCommand parent;

    @Option(names = {"--headless"}, description = "Run without TUI, print a summary at the end.")
    boolean headless;
    @Option(names = {"--topic"}, defaultValue = "morpheus_latency",
            description = "Ping topic (default: ${DEFAULT-VALUE}).")
    String topic;
    @Option(names = {"--interval"}, defaultValue = "1000", description = "Ping interval in ms (default: ${DEFAULT-VALUE}).")
    long intervalMs;
    @Option(names = {"--misses"}, defaultValue = "3", description = "Consecutive missed pings until DOWN (default: ${DEFAULT-VALUE}).")
    int misses;
    @Option(names = {"--timeout"}, defaultValue = "5000", description = "Ping timeout ms (default: ${DEFAULT-VALUE}).")
    long timeoutMs;
    @Option(names = {"--duration"}, defaultValue = "60", description = "Headless run duration in seconds (default: ${DEFAULT-VALUE}).")
    int durationSecs;
    @Option(names = {"--no-rs-watch"}, description = "Disable replica-set primary polling.")
    boolean noRsWatch;
    @Option(names = {"--json"}, description = "Print summary as JSON.")
    boolean json;
    @Option(names = {"--events-csv"}, description = "Write event log CSV to this file.")
    Path eventsCsv;
    @Option(names = {"--nodes-csv"}, description = "Write per-node summary CSV to this file.")
    Path nodesCsv;
    @Option(names = {"--samples-csv"}, description = "Write raw ping samples CSV to this file.")
    Path samplesCsv;
    @Option(names = {"--fail-on-outage"}, description = "Exit non-zero when at least one outage was recorded.")
    boolean failOnOutage;

    @Spec
    CommandSpec spec;

    private static final String[] HEADLESS_ONLY_OPTIONS = {
        "--interval", "--misses", "--timeout", "--duration", "--json",
        "--events-csv", "--nodes-csv", "--samples-csv", "--fail-on-outage"
    };

    @Override
    public Integer call() throws Exception {
        if (!headless) {
            MorpheusContext ctx = parent.context();
            warnIgnoredHeadlessOptions(ctx);
            MorpheusTui.launchView(ctx, "uptime", (viewName, viewCtx) -> new UptimeScreen(viewCtx, topic));
            return 0;
        }
        MorpheusContext ctx = parent.context();
        ctx.connect();
        var d = LatencyProbe.Config.defaults();
        var cfg = new LatencyProbe.Config(topic, false, LatencyProbe.Mode.INTERVAL, intervalMs, d.rate(),
                d.rampStartRate(), d.rampStepRate(), d.rampStepMs(), d.rampAbortLossPct(),
                1, 0, timeoutMs, d.bufferCapacity(), d.warmupMs());
        UptimeTracker tracker = new UptimeTracker(misses, System::currentTimeMillis);
        LatencyStats stats = new LatencyStats(cfg.bufferCapacity());
        final LatencyProbe[] ref = new LatencyProbe[1];
        LatencyProbe probe = new LatencyProbe(cfg, stats,
                m -> ctx.getMessaging().sendAndAwaitAsync(m, cfg.timeoutMs(), a -> ref[0].handleAnswer(a)),
                System::nanoTime, System::currentTimeMillis);
        ref[0] = probe;
        probe.setOutcomeListener(new PingOutcomeListener() {
            @Override public void onPingComplete(long seq, long sentTs, Set<String> answeredBy) {
                tracker.onPingComplete(seq, sentTs, answeredBy);
            }
            @Override public void onSendError(long sentTs) { tracker.recordSendError(); }
        });
        ReplicaSetWatcher watcher = null;
        if (!noRsWatch) {
            watcher = new ReplicaSetWatcher(new DriverHelloSource(ctx.getMorphium()), tracker, 1000);
            watcher.start();
        }
        probe.start();
        for (int i = 0; i < durationSecs; i++) Thread.sleep(1000);
        probe.stop();
        if (watcher != null) watcher.stop();
        // Wie LatencyCommand: vollen Timeout abwarten, damit der letzte Schwung Pings
        // komplett beantwortet oder ausgetimed ist, bevor final gesweept wird.
        Thread.sleep(cfg.timeoutMs());
        probe.sweep();
        var snap = tracker.snapshot();
        if (eventsCsv != null) writeQuiet(eventsCsv, UptimeCsv.eventsCsv(snap.events()));
        if (nodesCsv != null) writeQuiet(nodesCsv, UptimeCsv.nodesCsv(snap.nodes()));
        if (samplesCsv != null) writeQuiet(samplesCsv, UptimeCsv.samplesCsv(stats.snapshot().samples()));
        if (json) System.out.println(reportJson(snap));
        else printThemed(ctx, snap);
        return failOnOutage && totalOutages(snap) > 0 ? 2 : 0;
    }

    static int totalOutages(UptimeTracker.Snapshot s) {
        return s.nodes().stream().mapToInt(UptimeTracker.NodeStats::outages).sum();
    }

    static String reportJson(UptimeTracker.Snapshot s) {
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.ROOT, "{\"totalOutages\":%d,\"sendErrors\":%d,\"events\":%d,\"nodes\":[",
                totalOutages(s), s.sendErrors(), s.events().size()));
        for (int i = 0; i < s.nodes().size(); i++) {
            var n = s.nodes().get(i);
            if (i > 0) b.append(',');
            b.append(String.format(Locale.ROOT,
                    "{\"node\":\"%s\",\"state\":\"%s\",\"availabilityPct\":%.3f,\"outages\":%d,"
                    + "\"longestOutageMs\":%d,\"avgOutageMs\":%d}",
                    n.node(), n.state(), n.availabilityPct(), n.outages(),
                    n.longestOutageMs(), n.avgOutageMs()));
        }
        return b.append("]}").toString();
    }

    private void printThemed(MorpheusContext ctx, UptimeTracker.Snapshot s) {
        ctx.pr("[header1]uptime summary[r]  nodes: " + s.nodes().size()
                + "  outages: " + totalOutages(s) + "  events: " + s.events().size()
                + (s.sendErrors() > 0 ? "  [warning]sendErr:" + s.sendErrors() + "[r]" : ""));
        ctx.pr("[c3]" + ctx.getColumn("Node", 30) + " | " + ctx.getColumn("state", 6) + " | "
                + ctx.getColumn("avail%", 7) + " | " + ctx.getColumn("outages", 7) + " | "
                + ctx.getColumn("longest", 9) + " | " + ctx.getColumn("avg", 9));
        for (var n : s.nodes()) {
            ctx.pr(ctx.getColumn(n.node(), 30) + " | " + ctx.getColumn(String.valueOf(n.state()), 6) + " | "
                    + ctx.getColumn(String.format(Locale.ROOT, "%.1f", n.availabilityPct()), 7) + " | "
                    + ctx.getColumn(String.valueOf(n.outages()), 7) + " | "
                    + ctx.getColumn(n.longestOutageMs() + "ms", 9) + " | "
                    + ctx.getColumn(n.avgOutageMs() + "ms", 9));
        }
    }

    private void writeQuiet(Path p, String content) {
        try {
            Files.writeString(p, content);
        } catch (Exception e) {
            System.err.println("[uptime] csv write failed (" + p + "): " + e.getMessage());
        }
    }

    private void warnIgnoredHeadlessOptions(MorpheusContext ctx) {
        if (spec == null) return;
        CommandLine.ParseResult pr = spec.commandLine().getParseResult();
        for (String opt : HEADLESS_ONLY_OPTIONS) {
            if (pr.hasMatchedOption(opt)) {
                ctx.pr("[warning]--interval/--misses/--timeout/--duration/--json/--events-csv/"
                        + "--nodes-csv/--samples-csv/--fail-on-outage are ignored without --headless[r]");
                return;
            }
        }
    }
}

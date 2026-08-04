package de.caluga.morpheus.core.uptime;

import de.caluga.morpheus.core.latency.LatencySample;
import de.caluga.morpheus.core.latency.LatencyStats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** CSV-Formate für den Uptime-Export (Locale.ROOT, RFC-4180-Quoting für Freitext). */
public final class UptimeCsv {

    private UptimeCsv() { }

    public static String eventsCsv(List<UptimeTracker.Event> events) {
        StringBuilder b = new StringBuilder("ts,type,node,detail,durationMs\n");
        for (var e : events) {
            b.append(e.ts()).append(',').append(e.type()).append(',').append(quote(e.node()))
             .append(',').append(quote(e.detail()))
             .append(',').append(e.durationMs() == null ? "" : e.durationMs()).append('\n');
        }
        return b.toString();
    }

    public static String nodesCsv(List<UptimeTracker.NodeStats> nodes) {
        StringBuilder b = new StringBuilder("node,state,availabilityPct,outages,longestOutageMs,avgOutageMs\n");
        for (var n : nodes) {
            b.append(String.format(Locale.ROOT, "%s,%s,%.3f,%d,%d,%d\n",
                    quote(n.node()), n.state(), n.availabilityPct(), n.outages(),
                    n.longestOutageMs(), n.avgOutageMs()));
        }
        return b.toString();
    }

    public static String samplesCsv(List<LatencySample> samples) {
        StringBuilder b = new StringBuilder("seq,ts,responder,rttMs\n");
        for (var s : samples) {
            b.append(String.format(Locale.ROOT, "%d,%d,%s,%.3f\n",
                    s.seq(), s.sentTs(), quote(s.responderKey()), s.rttMs()));
        }
        return b.toString();
    }

    /** Schreibt events/nodes/samples als drei Dateien in dir; gibt die Pfade zurück. */
    public static List<Path> writeAll(Path dir, String stamp,
                                      UptimeTracker.Snapshot up, LatencyStats.Snapshot lat) throws IOException {
        List<Path> out = new ArrayList<>();
        out.add(write(dir.resolve("uptime-events-" + stamp + ".csv"), eventsCsv(up.events())));
        out.add(write(dir.resolve("uptime-nodes-" + stamp + ".csv"), nodesCsv(up.nodes())));
        out.add(write(dir.resolve("uptime-samples-" + stamp + ".csv"), samplesCsv(lat.samples())));
        return out;
    }

    private static Path write(Path p, String content) throws IOException {
        Files.writeString(p, content);
        return p;
    }

    private static String quote(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}

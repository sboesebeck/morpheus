package de.caluga.morpheus.core.latency;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** CSV rendering of the raw sample series (one row per answered ping), shared by the
 *  TUI export key and the headless --samples option. The summary CSV (one row per
 *  responder) lives in LatencyCommand; this is the time series for plotting/analysis. */
public final class LatencyCsv {

    private LatencyCsv() {}

    public static String samplesCsv(final List<LatencySample> samples) {
        final StringBuilder b = new StringBuilder("seq,sentTs,responder,host,rttMs,outboundMs,returnMs,warmup\n");
        for (final LatencySample s : samples) {
            b.append(s.seq()).append(',')
             .append(s.sentTs()).append(',')
             .append(csvField(s.sender())).append(',')
             .append(csvField(s.senderHost())).append(',')
             .append(String.format(Locale.ROOT, "%.3f", s.rttMs())).append(',')
             .append(num(s.outboundMs())).append(',')
             .append(num(s.returnMs())).append(',')
             .append(s.warmup() ? 1 : 0).append('\n');
        }
        return b.toString();
    }

    public static Path writeSamples(final Path file, final List<LatencySample> samples) throws IOException {
        Files.writeString(file, samplesCsv(samples), StandardCharsets.UTF_8);
        return file.toAbsolutePath();
    }

    private static String num(final Double d) {
        return d == null ? "" : String.format(Locale.ROOT, "%.3f", d);
    }

    private static String csvField(final String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}

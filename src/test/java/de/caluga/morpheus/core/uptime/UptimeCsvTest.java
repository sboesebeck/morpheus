package de.caluga.morpheus.core.uptime;

import de.caluga.morpheus.core.latency.LatencySample;
import de.caluga.morpheus.core.latency.LatencyStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class UptimeCsvTest {

    private UptimeTracker.Snapshot snap() {
        AtomicLong wall = new AtomicLong(0);
        UptimeTracker t = new UptimeTracker(1, wall::get);
        t.onPingComplete(1, 1000, Set.of("a/h1"));
        t.onPingComplete(2, 2000, Set.of());
        t.onPingComplete(3, 9000, Set.of("a/h1"));
        wall.set(10_000);
        return t.snapshot();
    }

    @Test
    void eventsCsvFormat() {
        String csv = UptimeCsv.eventsCsv(snap().events());
        String[] lines = csv.split("\n");
        assertEquals("ts,type,node,detail,durationMs", lines[0]);
        assertEquals("1000,LEARNED,a/h1,,", lines[1]);
        assertTrue(lines[2].startsWith("2000,DOWN,a/h1,"));
        assertTrue(lines[3].startsWith("9000,UP,a/h1,"));
        assertTrue(lines[3].endsWith(",7000"));
    }

    @Test
    void detailWithCommaIsQuoted() {
        AtomicLong wall = new AtomicLong(5);
        UptimeTracker t = new UptimeTracker(1, wall::get);
        t.recordEvent(UptimeTracker.Event.Type.RS_UNKNOWN, "rs", "boom, with \"quotes\"");
        String line = UptimeCsv.eventsCsv(t.snapshot().events()).split("\n")[1];
        assertEquals("5,RS_UNKNOWN,rs,\"boom, with \"\"quotes\"\"\",", line);
    }

    @Test
    void nodesCsvFormat() {
        String csv = UptimeCsv.nodesCsv(snap().nodes());
        String[] lines = csv.split("\n");
        assertEquals("node,state,availabilityPct,outages,longestOutageMs,avgOutageMs", lines[0]);
        assertTrue(lines[1].startsWith("a/h1,UP,"), "got: " + lines[1]);
        assertTrue(lines[1].endsWith(",1,7000,7000"));
    }

    @Test
    void samplesCsvFormat() {
        var s = new LatencySample(7, "a", "h1", 1.234, null, null, 4200);
        String csv = UptimeCsv.samplesCsv(List.of(s));
        String[] lines = csv.split("\n");
        assertEquals("seq,ts,responder,rttMs", lines[0]);
        assertEquals("7,4200,a/h1,1.234", lines[1]);
    }

    @Test
    void writeAllCreatesThreeFiles(@TempDir Path dir) throws Exception {
        LatencyStats st = new LatencyStats(10);
        st.recordAnswer(new LatencySample(1, "a", "h1", 1.0, null, null, 1000));
        List<Path> files = UptimeCsv.writeAll(dir, "20260804-140000", snap(), st.snapshot());
        assertEquals(3, files.size());
        assertTrue(Files.exists(dir.resolve("uptime-events-20260804-140000.csv")));
        assertTrue(Files.exists(dir.resolve("uptime-nodes-20260804-140000.csv")));
        assertTrue(Files.exists(dir.resolve("uptime-samples-20260804-140000.csv")));
        assertTrue(Files.readString(files.get(0)).startsWith("ts,type,node,detail,durationMs"));
    }
}

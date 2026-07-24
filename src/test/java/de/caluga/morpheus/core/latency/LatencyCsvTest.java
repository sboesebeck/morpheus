package de.caluga.morpheus.core.latency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LatencyCsvTest {

    @Test
    void oneRowPerSampleWithHeader() {
        String csv = LatencyCsv.samplesCsv(List.of(
                new LatencySample(1, "pong-1", "hostA", 2.5, 1.25, 1.25, 1000),
                new LatencySample(2, "pong-2", "hostB", 3.125, null, null, 1001)));
        String[] lines = csv.split("\n");
        assertEquals(3, lines.length);
        assertEquals("seq,sentTs,responder,host,rttMs,outboundMs,returnMs,warmup", lines[0]);
        assertEquals("1,1000,pong-1,hostA,2.500,1.250,1.250,0", lines[1]);
        assertEquals("2,1001,pong-2,hostB,3.125,,,0", lines[2]);
    }

    @Test
    void warmupSamplesFlaggedInColumn() {
        String csv = LatencyCsv.samplesCsv(List.of(
                new LatencySample(1, "p", "h", 2.0, null, null, 1000, true),
                new LatencySample(2, "p", "h", 2.0, null, null, 2000, false)));
        String[] lines = csv.split("\n");
        assertTrue(lines[1].endsWith(",1"), lines[1]);
        assertTrue(lines[2].endsWith(",0"), lines[2]);
    }

    @Test
    void quotesFieldsContainingCommas() {
        String csv = LatencyCsv.samplesCsv(List.of(
                new LatencySample(1, "a,b", "h\"x", 1.0, null, null, 5)));
        assertTrue(csv.contains("\"a,b\",\"h\"\"x\""), csv);
    }

    @Test
    void writesFile(@TempDir Path dir) throws Exception {
        Path written = LatencyCsv.writeSamples(dir.resolve("out.csv"),
                List.of(new LatencySample(1, "p", "h", 1.0, null, null, 5)));
        assertTrue(Files.exists(written));
        assertTrue(Files.readString(written).startsWith("seq,"));
    }
}

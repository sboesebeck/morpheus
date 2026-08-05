package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.core.latency.LatencySample;
import de.caluga.morpheus.core.latency.LatencyStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LatencyScreenTest {

    private LatencyStats.Snapshot snap(int nSamples) {
        LatencyStats st = new LatencyStats(1000);
        for (long i = 1; i <= nSamples; i++) {
            st.recordPingSent(i);
            st.recordAnswer(new LatencySample(i, "pong-1", "hostA", 2.0 + i % 5, 1.0, 1.0, 1000));
        }
        return st.snapshot();
    }

    /** Project pattern (see StatusScreenTest/GraphScreenTest): draw onto a DefaultVirtualTerminal's
     *  TextGraphics, then read the rendered characters back off the virtual terminal's buffer. */
    private DefaultVirtualTerminal terminal(int cols, int rows) throws Exception {
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new TerminalSize(cols, rows));
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        return vt;
    }

    private String textOf(DefaultVirtualTerminal vt) {
        TerminalSize size = vt.getTerminalSize();
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < size.getRows(); y++) {
            for (int x = 0; x < size.getColumns(); x++) {
                sb.append(vt.getCharacter(x, y).getCharacterString());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Test
    void emptyStateShowsHint() throws Exception {
        LatencyScreen s = new LatencyScreen(snap(0));
        DefaultVirtualTerminal vt = terminal(80, 24);
        TextGraphics g = vt.newTextGraphics();
        s.draw(g);
        assertTrue(textOf(vt).contains("press s to start"), "empty state hint expected");
    }

    @Test
    void tableShowsResponderRow() throws Exception {
        LatencyScreen s = new LatencyScreen(snap(50));
        DefaultVirtualTerminal vt = terminal(100, 30);
        TextGraphics g = vt.newTextGraphics();
        s.draw(g);
        String text = textOf(vt);
        assertTrue(text.contains("pong-1/hostA"));
        assertTrue(text.contains("p99"));
    }

    @Test
    void histogramToggles() throws Exception {
        LatencyScreen s = new LatencyScreen(snap(50));
        assertFalse(s.histogramVisible());
        s.onKey(KeyStroke.fromString("h"));
        assertTrue(s.histogramVisible());
        DefaultVirtualTerminal vt = terminal(100, 30);
        TextGraphics g = vt.newTextGraphics();
        s.draw(g);
        String text = textOf(vt);
        assertTrue(text.contains("≤1ms") || text.contains("<=1ms"));
        s.onKey(KeyStroke.fromString("h"));
        assertFalse(s.histogramVisible());
    }

    @Test
    void modePayloadThreadCycling() {
        LatencyScreen s = new LatencyScreen(snap(0));
        var m0 = s.mode();
        s.onKey(KeyStroke.fromString("m"));
        assertNotEquals(m0, s.mode());
        s.onKey(KeyStroke.fromString("P"));
        assertEquals(1024, s.payloadBytes());
        s.onKey(KeyStroke.fromString("T"));
        assertEquals(2, s.senderThreads());
    }

    @Test
    void lowercaseKeysDecreaseAndClampAtBounds() {
        LatencyScreen s = new LatencyScreen(snap(0));
        s.onKey(KeyStroke.fromString("p"));                     // already at minimum
        assertEquals(0, s.payloadBytes());
        s.onKey(KeyStroke.fromString("t"));
        assertEquals(1, s.senderThreads());
        s.onKey(KeyStroke.fromString("P"));
        s.onKey(KeyStroke.fromString("p"));                     // up then down again
        assertEquals(0, s.payloadBytes());
        for (int i = 0; i < 10; i++) s.onKey(KeyStroke.fromString("T"));   // clamp at maximum
        assertEquals(8, s.senderThreads());
        s.onKey(KeyStroke.fromString("t"));
        assertEquals(4, s.senderThreads());
    }

    @Test
    void helpLineShowsCurrentPayloadAndThreads() throws Exception {
        LatencyScreen s = new LatencyScreen(snap(5));
        s.onKey(KeyStroke.fromString("P"));
        s.onKey(KeyStroke.fromString("T"));
        DefaultVirtualTerminal vt = terminal(120, 30);
        s.draw(vt.newTextGraphics());
        String text = textOf(vt);
        assertTrue(text.contains("payload:1k"), "expected payload value in help line, got:\n" + text);
        assertTrue(text.contains("threads:2"), "expected thread count in help line, got:\n" + text);
    }

    @Test
    void exportWritesSamplesCsv(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        LatencyScreen s = new LatencyScreen(snap(10));
        s.setExportDir(dir);
        s.onKey(KeyStroke.fromString("e"));
        assertNotNull(s.exportNote());
        assertTrue(s.exportNote().startsWith("exported 10 samples"), s.exportNote());
        try (var files = java.nio.file.Files.list(dir)) {
            var written = files.toList();
            assertEquals(1, written.size());
            String content = java.nio.file.Files.readString(written.get(0));
            assertTrue(content.startsWith("seq,sentTs,responder,host,rttMs"));
            assertEquals(11, content.strip().split("\n").length);   // header + 10 samples
        }
    }

    @Test
    void exportWithoutSamplesOnlyNotes(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        LatencyScreen s = new LatencyScreen(snap(0));
        s.setExportDir(dir);
        s.onKey(KeyStroke.fromString("e"));
        assertTrue(s.exportNote().contains("nothing to export"), s.exportNote());
        try (var files = java.nio.file.Files.list(dir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void quitAndPop() {
        LatencyScreen s = new LatencyScreen(snap(0));
        assertEquals(de.caluga.morpheus.tui.Screen.Result.Kind.QUIT, s.onKey(KeyStroke.fromString("q")).kind());
        assertEquals(de.caluga.morpheus.tui.Screen.Result.Kind.POP, s.onKey(new KeyStroke(KeyType.Escape)).kind());
    }

    @Test
    void startWithoutBackendStaysStopped() {
        LatencyScreen s = new LatencyScreen(snap(0));   // test seam: no probe
        s.onKey(KeyStroke.fromString("s"));
        assertFalse(s.isRunning());                     // must not NPE
    }

    @Test
    void nullContextConstructsAndStaysStoppedOnStart() {
        LatencyScreen s = new LatencyScreen((de.caluga.morpheus.cli.MorpheusContext) null);
        s.onKey(KeyStroke.fromString("s"));
        assertFalse(s.isRunning());                     // must not NPE
    }

    @Test
    void topicCtorReflectsCustomTopicWithoutNpe() {
        LatencyScreen s = new LatencyScreen((de.caluga.morpheus.cli.MorpheusContext) null, "custom_topic", false);
        assertEquals("custom_topic", s.currentConfig().topic());
        s.onKey(KeyStroke.fromString("s"));
        assertFalse(s.isRunning());                      // must not NPE
    }

    @Test
    void sendErrorsShowInHeader() throws Exception {
        LatencyStats st = new LatencyStats(1000);
        st.recordPingSent(1);
        st.recordSendError();
        LatencyScreen s = new LatencyScreen(st.snapshot());
        DefaultVirtualTerminal vt = terminal(120, 30);
        TextGraphics g = vt.newTextGraphics();
        s.draw(g);
        String text = textOf(vt);
        assertTrue(text.contains("sendErr:1"), "expected send error indicator in header, got:\n" + text);
    }
}

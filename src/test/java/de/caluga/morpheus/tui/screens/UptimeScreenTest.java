package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.core.latency.LatencySample;
import de.caluga.morpheus.core.latency.LatencyStats;
import de.caluga.morpheus.core.uptime.UptimeTracker;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class UptimeScreenTest {

    private final AtomicLong wall = new AtomicLong(0);

    /** Tracker mit einem gelernten Knoten, einem abgeschlossenen Ausfall und einem Primary-Wechsel. */
    private UptimeTracker.Snapshot upSnap() {
        UptimeTracker t = new UptimeTracker(3, wall::get);
        t.onPingComplete(1, 1000, Set.of("pong-1/hostA"));
        t.onPingComplete(2, 2000, Set.of());
        t.onPingComplete(3, 3000, Set.of());
        t.onPingComplete(4, 4000, Set.of());
        wall.set(5000);
        t.recordEvent(UptimeTracker.Event.Type.PRIMARY_CHANGE, "rs", "h1:27017 → h2:27017");
        t.onPingComplete(9, 9000, Set.of("pong-1/hostA"));
        wall.set(10_000);
        return t.snapshot();
    }

    private LatencyStats.Snapshot latSnap() {
        LatencyStats st = new LatencyStats(100);
        st.recordPingSent(1);
        st.recordAnswer(new LatencySample(1, "pong-1", "hostA", 1.3, null, null, 1000));
        return st.snapshot();
    }

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
            for (int x = 0; x < size.getColumns(); x++) sb.append(vt.getCharacter(x, y).getCharacterString());
            sb.append('\n');
        }
        return sb.toString();
    }

    @Test
    void emptyStateShowsHint() throws Exception {
        UptimeScreen s = new UptimeScreen(new UptimeTracker(3, wall::get).snapshot(), latSnap());
        DefaultVirtualTerminal vt = terminal(80, 24);
        s.draw(vt.newTextGraphics());
        assertTrue(textOf(vt).contains("press s to start monitoring"));
    }

    @Test
    void rendersTableTimelineAndEvents() throws Exception {
        UptimeScreen s = new UptimeScreen(upSnap(), latSnap());
        DefaultVirtualTerminal vt = terminal(110, 30);
        s.draw(vt.newTextGraphics());
        String text = textOf(vt);
        assertTrue(text.contains("pong-1/hostA"), "node row expected");
        assertTrue(text.contains("Ausfälle"), "table header expected");
        assertTrue(text.contains("DOWN"), "down event expected in log");
        assertTrue(text.contains("PRIMARY_CHANGE"), "primary change event expected");
        assertTrue(text.contains("1.3"), "lastRTT from latency stats expected");
    }

    @Test
    void missesCycleAndIntervalAdjust() {
        UptimeScreen s = new UptimeScreen(upSnap(), latSnap());
        assertEquals(3, s.missThreshold());
        s.onKey(KeyStroke.fromString("n"));
        assertEquals(5, s.missThreshold());
        s.onKey(KeyStroke.fromString("n"));
        assertEquals(10, s.missThreshold());
        s.onKey(KeyStroke.fromString("n"));
        assertEquals(1, s.missThreshold());
        assertEquals(1000, s.intervalMs());
        s.onKey(KeyStroke.fromString("+"));
        assertEquals(1250, s.intervalMs());
        s.onKey(KeyStroke.fromString("-"));
        s.onKey(KeyStroke.fromString("-"));
        s.onKey(KeyStroke.fromString("-"));
        s.onKey(KeyStroke.fromString("-"));
        assertEquals(250, s.intervalMs(), "must clamp at 250ms");
    }

    @Test
    void rsWatchToggles() {
        UptimeScreen s = new UptimeScreen(upSnap(), latSnap());
        assertTrue(s.rsWatchEnabled());
        s.onKey(KeyStroke.fromString("r"));
        assertFalse(s.rsWatchEnabled());
    }

    @Test
    void eventScrollClamps() {
        UptimeScreen s = new UptimeScreen(upSnap(), latSnap());
        assertEquals(0, s.eventScroll());
        s.onKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(0, s.eventScroll(), "cannot scroll below newest");
        for (int i = 0; i < 100; i++) s.onKey(new KeyStroke(KeyType.ArrowUp));
        assertTrue(s.eventScroll() < 100, "scroll must clamp to event count");
    }

    @Test
    void quitAndPop() {
        UptimeScreen s = new UptimeScreen(upSnap(), latSnap());
        assertEquals(de.caluga.morpheus.tui.Screen.Result.Kind.QUIT, s.onKey(KeyStroke.fromString("q")).kind());
        assertEquals(de.caluga.morpheus.tui.Screen.Result.Kind.POP, s.onKey(new KeyStroke(KeyType.Escape)).kind());
    }

    @Test
    void startWithoutBackendStaysStopped() {
        UptimeScreen s = new UptimeScreen(upSnap(), latSnap());
        s.onKey(KeyStroke.fromString("s"));
        assertFalse(s.isRunning());
    }

    @Test
    void nullContextConstructsAndStaysStoppedOnStart() {
        UptimeScreen s = new UptimeScreen((de.caluga.morpheus.cli.MorpheusContext) null);
        s.onKey(KeyStroke.fromString("s"));
        assertFalse(s.isRunning());
    }

    @Test
    void tinyTerminalDoesNotCrash() throws Exception {
        UptimeScreen s = new UptimeScreen(upSnap(), latSnap());
        DefaultVirtualTerminal vt = terminal(8, 4);
        s.draw(vt.newTextGraphics());   // must not throw
    }

    @Test
    void writeKeyProducesThreeCsvFiles(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        UptimeScreen s = new UptimeScreen(upSnap(), latSnap());
        s.csvDir(dir);
        s.onKey(KeyStroke.fromString("w"));
        try (var files = java.nio.file.Files.list(dir)) {
            assertEquals(3, files.count());
        }
        assertNotNull(s.csvStatus());
        assertTrue(s.csvStatus().startsWith("CSV: uptime-"), s.csvStatus());
    }
}

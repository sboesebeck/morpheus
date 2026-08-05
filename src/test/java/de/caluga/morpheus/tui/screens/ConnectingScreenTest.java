package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.config.ConfigurationManager;
import de.caluga.morpheus.tui.Screen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectingScreenTest {

    @TempDir Path tmp;

    private MorpheusContext unconnectedCtx() {
        return new MorpheusContext(new ConfigurationManager(tmp.resolve("m.properties").toString()));
    }

    private Screen dummyView() {
        return new Screen() {
            public void draw(TextGraphics g) {}
            public Result onKey(KeyStroke k) { return Result.stay(); }
        };
    }

    private void await(java.util.function.BooleanSupplier cond, long maxMs) throws Exception {
        long deadline = System.currentTimeMillis() + maxMs;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) return;
            Thread.sleep(20);
        }
        fail("condition not met within " + maxMs + "ms");
    }

    @Test
    void successHandsOffViaTickReplace() throws Exception {
        Screen view = dummyView();
        ConnectingScreen s = new ConnectingScreen("prod", "messages",
                () -> unconnectedCtx(),                 // fake connect returns immediately
                (name, ctx) -> view);
        // wait via the non-mutating isReady() seam (tick() has a side effect — call it once)
        await(() -> s.isReady(), 2000);
        Screen.Result r = s.tick();
        assertEquals(Screen.Result.Kind.REPLACE, r.kind());
        assertSame(view, r.next());
    }

    @Test
    void failureShowsErrorAndEscPops() throws Exception {
        ConnectingScreen s = new ConnectingScreen("prod", "messages",
                () -> { throw new RuntimeException("no route to host"); },
                (name, ctx) -> dummyView());
        await(() -> s.isFailed(), 2000);
        // Esc returns to the launcher
        assertEquals(Screen.Result.Kind.POP, s.onKey(new KeyStroke(KeyType.Escape)).kind());
        // render the error state without throwing
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal();
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics());
        ts.stopScreen();
    }

    @Test
    void failureShowsTheRealCauseNotJustTheOuterWrapperMessage() throws Exception {
        Exception cause = new javax.net.ssl.SSLHandshakeException("PKIX path building failed: unable to find valid certification path");
        ConnectingScreen s = new ConnectingScreen("prod", "messages",
                () -> { throw new IllegalStateException("Failed to connect to MongoDB after 10 attempts", cause); },
                (name, ctx) -> dummyView());
        await(() -> s.isFailed(), 2000);

        // The exact bug this guards against: a TUI screen that only ever renders the outer
        // message hides the real reason a connection failed (see MorphiumConnectionFactory,
        // which now attaches the driver's real connect failure as the cause).
        assertTrue(s.errorText().contains("SSLHandshakeException"),
                "must surface the cause's type, not just the generic outer message: " + s.errorText());
        assertTrue(s.errorText().contains("PKIX path building failed"), s.errorText());
    }

    /** Reads back every character actually painted onto the virtual terminal, one row per
     * string - this is what the previous test above cannot catch: {@code errorText()} can hold
     * the full cause chain while {@code draw()} still clips it at the terminal edge. */
    private String[] renderedRows(DefaultVirtualTerminal vt) {
        com.googlecode.lanterna.TerminalSize size = vt.getTerminalSize();
        String[] rows = new String[size.getRows()];
        for (int y = 0; y < size.getRows(); y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < size.getColumns(); x++) {
                row.append(vt.getCharacter(x, y).getCharacter());
            }
            rows[y] = row.toString();
        }
        return rows;
    }

    @Test
    void failureRenderingWrapsLongCauseChainsInsteadOfClippingThemAtTheTerminalEdge() throws Exception {
        // A long, real-shaped cause chain - the kind Failures.causeDetail() actually produces -
        // that does not fit on a single 80-column row.
        String longCause = "MorphiumDriverException: No primary node found - not connected yet? - last "
                + "connection error: javax.net.ssl.SSLHandshakeException: PKIX path building failed: "
                + "unable to find valid certification path to requested target (root cause: "
                + "SSLHandshakeException: PKIX path building failed)";
        ConnectingScreen s = new ConnectingScreen("prod", "messages",
                () -> { throw new IllegalStateException("Failed to connect to MongoDB after 10 attempts - " + longCause); },
                (name, ctx) -> dummyView());
        await(() -> s.isFailed(), 2000);

        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new com.googlecode.lanterna.TerminalSize(80, 24));
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics());
        ts.refresh(); // TerminalScreen double-buffers; without this the virtual terminal's own
                       // character grid (what renderedRows() reads) stays blank.

        // Read back BEFORE stopScreen(): stopScreen() exits the alternate screen buffer, which
        // wipes the virtual terminal's visible content - reading after it sees nothing but blanks.
        String rendered = String.join("\n", renderedRows(vt));
        ts.stopScreen();
        assertTrue(rendered.contains("SSLHandshakeException"),
                "the cause's type must actually be painted on screen, not just held in errorText(): \n" + rendered);
        assertTrue(rendered.contains("PKIX path building failed"),
                "the cause's message must actually be painted on screen, not clipped: \n" + rendered);
    }

    @Test
    void cancelWhileConnectingClosesTheBuiltContext() throws Exception {
        // A connector that blocks briefly, then returns a ctx whose close we can observe.
        boolean[] closed = {false};
        MorpheusContext spyCtx = new MorpheusContext(
                new ConfigurationManager(tmp.resolve("m.properties").toString())) {
            @Override public synchronized void close() { closed[0] = true; super.close(); }
        };
        ConnectingScreen s = new ConnectingScreen("prod", "messages",
                () -> { Thread.sleep(150); return spyCtx; },
                (name, ctx) -> dummyView());
        // cancel before the connect finishes
        s.onClose();
        // after the connector returns, it must close the ctx because we cancelled
        await(() -> closed[0], 2000);
        assertTrue(closed[0], "cancelled connect must close the context it built");
    }

    @Test
    void spinnerDrawWhileConnecting() throws Exception {
        ConnectingScreen s = new ConnectingScreen("prod", "messages",
                () -> { Thread.sleep(300); return unconnectedCtx(); },
                (name, ctx) -> dummyView());
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal();
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics()); // CONNECTING spinner, must not throw
        ts.stopScreen();
    }

    @Test
    void wrapBreaksOnSpacesWithinWidthAndKeepsEveryCharacter() {
        java.util.List<String> lines = ConnectingScreen.wrap("Failed to connect to MongoDB after 10 attempts", 20);
        for (String line : lines) {
            assertTrue(line.length() <= 20, "line exceeds width: '" + line + "'");
        }
        assertEquals("Failed to connect to MongoDB after 10 attempts", String.join(" ", lines));
    }

    @Test
    void wrapForcesABreakOnAWordLongerThanTheWidth() {
        // No spaces at all - must still respect the width instead of returning one giant line.
        java.util.List<String> lines = ConnectingScreen.wrap("a".repeat(50), 20);
        assertTrue(lines.size() >= 3, "expected at least 3 lines for a 50-char unbreakable string: " + lines);
        for (String line : lines) {
            assertTrue(line.length() <= 20, "line exceeds width: '" + line + "'");
        }
    }

    @Test
    void ctxConstructorConnectsPassedContextThenHandsOff() throws Exception {
        boolean[] connected = {false};
        MorpheusContext ctx = new MorpheusContext(
                new ConfigurationManager(tmp.resolve("m.properties").toString())) {
            @Override public synchronized void connect() { connected[0] = true; } // no real DB
        };
        Screen view = dummyView();
        ConnectingScreen s = new ConnectingScreen(ctx, "messages", (name, c) -> view);
        await(() -> s.isReady(), 2000);
        assertTrue(connected[0], "ctx constructor must connect the passed context");
        Screen.Result r = s.tick();
        assertEquals(Screen.Result.Kind.REPLACE, r.kind());
        assertSame(view, r.next());
    }
}

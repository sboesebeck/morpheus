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
}

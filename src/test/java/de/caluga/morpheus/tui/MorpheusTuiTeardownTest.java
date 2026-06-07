package de.caluga.morpheus.tui;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MorpheusTuiTeardownTest {

    /** A throwing onClose must not crash the run loop. */
    @Test
    void throwingOnCloseDoesNotCrashLoop() throws Exception {
        Screen inner = new Screen() {
            public void draw(TextGraphics g) {}
            public Result onKey(KeyStroke k) { return Result.pop(); }
            public void onClose() { throw new RuntimeException("driver boom"); }
        };
        Screen outer = new Screen() {
            private int n = 0;
            public void draw(TextGraphics g) {}
            public Result onKey(KeyStroke k) { return (++n == 1) ? Result.push(inner) : Result.quit(); }
        };

        DefaultVirtualTerminal vt = new DefaultVirtualTerminal();
        TerminalScreen screen = new TerminalScreen(vt);
        vt.addInput(new KeyStroke('a', false, false)); // push inner
        vt.addInput(new KeyStroke('b', false, false)); // inner pop -> onClose throws
        vt.addInput(new KeyStroke('c', false, false)); // outer quit

        new MorpheusTui(screen, null).run(outer); // must return, not throw
        assertTrue(true);
    }
}

package de.caluga.morpheus.tui;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class IdleTickTest {

    /** A screen whose tick() REPLACEs itself must transition with NO key input. */
    @Test
    void tickReplaceTransitionsWithoutKeypress() throws Exception {
        AtomicBoolean nextDrawn = new AtomicBoolean(false);

        Screen next = new Screen() {
            public void draw(TextGraphics g) { nextDrawn.set(true); }
            public Result onKey(KeyStroke k) { return Result.stay(); }
            public Result tick() { return Result.quit(); } // end the loop once reached
        };
        Screen first = new Screen() {
            private boolean done = false;
            public void draw(TextGraphics g) {}
            public Result onKey(KeyStroke k) { return Result.stay(); }
            public Result tick() {
                if (done) return Result.stay();
                done = true;
                return Result.replace(next);
            }
        };

        DefaultVirtualTerminal vt = new DefaultVirtualTerminal();
        TerminalScreen screen = new TerminalScreen(vt);
        // No buffered keys → pollInput() returns null → tick() path is exercised.

        new MorpheusTui(screen, null).run(first); // must return, not hang
        assertTrue(nextDrawn.get(), "tick REPLACE must transition to next without a keypress");
    }
}

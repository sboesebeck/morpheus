package de.caluga.morpheus.tui;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScreenClosesOnPopTest {
    @Test
    void poppedScreenGetsOnClose() throws Exception {
        AtomicBoolean innerClosed = new AtomicBoolean(false);

        Screen inner = new Screen() {
            public void draw(TextGraphics g) {}
            public Result onKey(KeyStroke k) { return Result.pop(); }
            @Override public void onClose() { innerClosed.set(true); }
        };
        Screen outer = new Screen() {
            private int n = 0;
            public void draw(TextGraphics g) {}
            public Result onKey(KeyStroke k) {
                n++;
                if (n == 1) return Result.push(inner);
                return Result.quit();
            }
        };

        DefaultVirtualTerminal vt = new DefaultVirtualTerminal();
        TerminalScreen screen = new TerminalScreen(vt);
        vt.addInput(new KeyStroke('a', false, false)); // outer -> push(inner)
        vt.addInput(new KeyStroke('b', false, false)); // inner -> pop  (onClose fires)
        vt.addInput(new KeyStroke('c', false, false)); // outer -> quit

        new MorpheusTui(screen, null).run(outer);
        assertTrue(innerClosed.get(), "popped screen must receive onClose()");
    }
}

package de.caluga.morpheus.tui;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.tui.screens.PlaceholderScreen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MorpheusTuiTest {

    @Test
    void quitKeyEndsTheLoop() throws Exception {
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal();
        TerminalScreen screen = new TerminalScreen(vt);
        // Feed a 'q' so the loop terminates immediately.
        vt.addInput(new KeyStroke('q', false, false));

        MorpheusTui tui = new MorpheusTui(screen, null);
        long start = System.currentTimeMillis();
        tui.run(new PlaceholderScreen());
        // Returning at all proves the loop ended on QUIT (no hang).
        assertTrue(System.currentTimeMillis() - start < 5000);
    }
}

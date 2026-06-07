package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.core.StatusPinger;
import de.caluga.morpheus.tui.Screen;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StatusScreenTest {

    @Test
    void escPops() {
        StatusScreen s = new StatusScreen(List.of());
        assertEquals(Screen.Result.Kind.POP, s.onKey(new KeyStroke(KeyType.Escape)).kind());
    }

    @Test
    void rTriggersRefreshAndStays() {
        StatusScreen s = new StatusScreen(List.of());
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('r', false, false)).kind());
    }

    @Test
    void rendersRosterRowsWithoutError() throws Exception {
        StatusScreen s = new StatusScreen(List.of(
                new StatusPinger.NodeEntry("worker1", "h1", 18, List.of("order.created", "cache.sync")),
                new StatusPinger.NodeEntry("worker2", "h2", 19, List.of("order.created"))));
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal();
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics());
        ts.stopScreen();
    }
}

package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.core.NodeRegistry;
import de.caluga.morpheus.tui.Screen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GraphScreenTest {

    @Test
    void escPopsAndPToggles() {
        GraphScreen s = new GraphScreen(new NodeRegistry("self"));
        assertEquals(Screen.Result.Kind.POP, s.onKey(new KeyStroke(KeyType.Escape)).kind());
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('p', false, false)).kind());
        assertEquals(Screen.Result.Kind.QUIT, s.onKey(new KeyStroke('q', false, false)).kind());
    }

    @Test
    void rendersSeededRegistryNarrowAndWide() throws Exception {
        NodeRegistry r = new NodeRegistry("self");
        r.observeSend("hermes", "h1", 1000);
        r.observeRecv("worker1", "h2", 1000);
        r.markListener("worker1", "h2", "order.created", 1000);
        r.observeRecv("worker2", "h3", 1000);
        GraphScreen s = new GraphScreen(r);
        renderOk(s, 120, 40);
        renderOk(s, 28, 8);     // below the minimum -> hint, must not throw
    }

    @Test
    void rendersEmptyRegistryWithoutError() throws Exception {
        renderOk(new GraphScreen(new NodeRegistry("self")), 100, 30);
    }

    private void renderOk(GraphScreen s, int cols, int rows) throws Exception {
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new com.googlecode.lanterna.TerminalSize(cols, rows));
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics());
        ts.stopScreen();
    }
}

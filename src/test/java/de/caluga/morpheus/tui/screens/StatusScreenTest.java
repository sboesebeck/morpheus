package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.core.NodeStatus;
import de.caluga.morpheus.tui.Screen;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class StatusScreenTest {

    private NodeStatus node(String sender, String host) {
        return new NodeStatus(sender, host, 18L, 100L, 400L, 97.5, 3L, 10L, 0L, 1234L, 42, Map.of());
    }

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
    void enterOnSelectedNodePushesDetail() {
        StatusScreen s = new StatusScreen(List.of(node("w1", "h1"), node("w2", "h2")));
        Screen.Result r = s.onKey(new KeyStroke(KeyType.Enter));
        assertEquals(Screen.Result.Kind.PUSH, r.kind());
        assertTrue(r.next() instanceof NodeDetailScreen);
    }

    @Test
    void enterOnEmptyRosterStays() {
        StatusScreen s = new StatusScreen(List.of());
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke(KeyType.Enter)).kind());
    }

    @Test
    void arrowKeysMoveSelectionWithinBounds() {
        StatusScreen s = new StatusScreen(List.of(node("w1", "h1"), node("w2", "h2")));
        assertEquals(0, s.selectedIndex());
        s.onKey(new KeyStroke(KeyType.ArrowUp));        // clamp at 0
        assertEquals(0, s.selectedIndex());
        s.onKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(1, s.selectedIndex());
        s.onKey(new KeyStroke(KeyType.ArrowDown));      // clamp at last
        assertEquals(1, s.selectedIndex());
    }

    @Test
    void plusMinusStepThroughTimeoutPresets() {
        StatusScreen s = new StatusScreen(List.of(node("w1", "h1")));
        assertEquals(5000, s.currentTimeoutMs());
        s.onKey(new KeyStroke('+', false, false));      // 5s -> 10s
        assertEquals(10000, s.currentTimeoutMs());
        s.onKey(new KeyStroke('-', false, false));      // -> 5s
        s.onKey(new KeyStroke('-', false, false));      // -> 2s (clamp)
        assertEquals(2000, s.currentTimeoutMs());
    }

    @Test
    void rendersOverviewNarrowAndWide() throws Exception {
        StatusScreen s = new StatusScreen(List.of(
                node("worker1", "h1"),
                new NodeStatus("worker2", "h2", 19L, null, null, null, null, null, null, null, null, Map.of())));
        renderOk(s, 200, 40);
        renderOk(s, 40, 12);
    }

    private void renderOk(StatusScreen s, int cols, int rows) throws Exception {
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new com.googlecode.lanterna.TerminalSize(cols, rows));
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics());
        ts.stopScreen();
    }
}

package de.caluga.morpheus.tui.widget;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class PickerOverlayTest {

    private PickerOverlay picker() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("orders", "orders");
        labels.put("order.created", "order.created");
        labels.put("cache.sync", "cache.sync");
        return new PickerOverlay("Topics", List.of("orders", "order.created", "cache.sync"),
                labels, Set.of());
    }

    @Test
    void spaceTogglesCurrentItem() {
        PickerOverlay p = picker();
        assertEquals(PickerOverlay.Outcome.STAY, p.onKey(new KeyStroke(' ', false, false)));
        assertTrue(p.selection().contains("orders"));
        p.onKey(new KeyStroke(' ', false, false));
        assertFalse(p.selection().contains("orders"));
    }

    @Test
    void typingFiltersVisibleItems() {
        PickerOverlay p = picker();
        p.onKey(new KeyStroke('o', false, false));
        p.onKey(new KeyStroke('r', false, false));
        p.onKey(new KeyStroke('d', false, false));
        List<String> vis = p.visibleItems();
        assertTrue(vis.contains("orders") && vis.contains("order.created"));
        assertFalse(vis.contains("cache.sync"));
    }

    @Test
    void tabSelectsAllMatches() {
        PickerOverlay p = picker();
        p.onKey(new KeyStroke('o', false, false));   // matches "orders","order.created"
        p.onKey(new KeyStroke(KeyType.Tab));
        assertTrue(p.selection().contains("orders"));
        assertTrue(p.selection().contains("order.created"));
        assertFalse(p.selection().contains("cache.sync"));
    }

    @Test
    void enterCommitsEscCancels() {
        PickerOverlay p = picker();
        p.onKey(new KeyStroke(' ', false, false));
        assertEquals(PickerOverlay.Outcome.COMMIT, p.onKey(new KeyStroke(KeyType.Enter)));
        assertEquals(PickerOverlay.Outcome.CANCEL, picker().onKey(new KeyStroke(KeyType.Escape)));
    }

    @Test
    void labelLookupAndRender() throws Exception {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("hermes", "hermes   host-7");
        PickerOverlay p = new PickerOverlay("Knoten", List.of("hermes"), labels, Set.of());
        assertEquals("hermes   host-7", p.labelOf("hermes"));
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new com.googlecode.lanterna.TerminalSize(120, 40));
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        p.draw(ts.newTextGraphics());
        ts.stopScreen();
    }
}

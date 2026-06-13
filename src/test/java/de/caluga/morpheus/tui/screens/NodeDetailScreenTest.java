package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.core.NodeStatus;
import de.caluga.morpheus.tui.Screen;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class NodeDetailScreenTest {

    private NodeStatus richNode() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("jvm.heap.used", 100L);
        raw.put("jvm.heap.max", 400L);
        raw.put("jvm.threads.active", 5);
        Map<String, Object> cache = new HashMap<>();
        cache.put("CHITSPERC", 97.5);
        raw.put("morphium.cachestats", cache);
        Map<String, Object> drv = new HashMap<>();
        drv.put("CONNECTIONS_IN_USE", 3.0);
        raw.put("morphium.driver.stats", drv);
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("database", "test");
        raw.put("morphium.config", cfg);
        return new NodeStatus("w", "h", 10, 100L, 400L, 97.5, 3L, 10L, 0L, 1L, 5, raw);
    }

    private int lineIndex(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).contains(needle)) return i;
        return -1;
    }

    @Test
    void buildsGroupedSectionsWithConfigLast() {
        NodeDetailScreen s = new NodeDetailScreen(richNode());
        List<String> lines = s.buildLines();
        String all = String.join("\n", lines);
        assertTrue(all.contains("── JVM / Mem ──"), "JVM section");
        assertTrue(all.contains("── Cache ──"), "Cache section");
        assertTrue(all.contains("── Driver ──"), "Driver section");
        assertTrue(all.contains("── Config ──"), "Config section");
        assertTrue(all.contains("Hit%"), "cache hit label");
        assertTrue(all.contains("database = test"), "config property rendered");
        int cacheIdx = lineIndex(lines, "── Cache ──");
        int cfgIdx = lineIndex(lines, "── Config ──");
        assertTrue(cfgIdx > cacheIdx && cfgIdx > lineIndex(lines, "── Driver ──"),
                "Config must be the last section");
    }

    @Test
    void escPopsAndArrowsStay() {
        NodeDetailScreen s = new NodeDetailScreen(richNode());
        assertEquals(Screen.Result.Kind.POP, s.onKey(new KeyStroke(KeyType.Escape)).kind());
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke(KeyType.ArrowDown)).kind());
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke(KeyType.ArrowUp)).kind());
    }

    @Test
    void rendersAfterScrollingPastEndWithoutError() throws Exception {
        NodeDetailScreen s = new NodeDetailScreen(richNode());
        for (int i = 0; i < 200; i++) s.onKey(new KeyStroke(KeyType.ArrowDown)); // push scroll way past content
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new com.googlecode.lanterna.TerminalSize(80, 10));
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics()); // clamp must keep this in-bounds
        ts.stopScreen();
    }
}

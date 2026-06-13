package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import de.caluga.morpheus.core.NodeStatus;
import de.caluga.morpheus.core.StatusFormat;
import de.caluga.morpheus.tui.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Full status detail of one node, grouped into sections (Config last), scrollable.
 *  Pure render of a provided NodeStatus — no backend, no context ownership. */
public class NodeDetailScreen implements Screen {

    private final NodeStatus node;
    private int scroll = 0;

    public NodeDetailScreen(NodeStatus node) {
        this.node = node;
    }

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) return Result.stay();
        if (key.getCharacter() != null && key.getCharacter() == 'q') return Result.quit();
        if (key.getKeyType() == KeyType.Escape) return Result.pop();
        if (key.getKeyType() == KeyType.ArrowDown) { scroll++; return Result.stay(); }
        if (key.getKeyType() == KeyType.ArrowUp) { if (scroll > 0) scroll--; return Result.stay(); }
        return Result.stay();
    }

    @Override
    public void draw(TextGraphics g) {
        int width = g.getSize().getColumns();
        int height = g.getSize().getRows();
        List<String> lines = buildLines();

        int visible = Math.max(1, height - 2);
        int maxScroll = Math.max(0, lines.size() - visible);
        if (scroll > maxScroll) scroll = maxScroll;

        for (int i = 0; i < visible && scroll + i < lines.size(); i++) {
            String ln = lines.get(scroll + i);
            TextColor c = ln.startsWith("Knoten ") ? TextColor.ANSI.CYAN_BRIGHT
                    : ln.startsWith("── ") ? TextColor.ANSI.YELLOW
                    : TextColor.ANSI.DEFAULT;
            g.setForegroundColor(c);
            g.putString(2, i, trunc(ln, width - 3));
        }
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, height - 1, "[↑/↓] scrollen  [esc] zurück  [q] quit");
    }

    /** Builds the grouped, ordered display lines from the raw status map. Config is always last. */
    List<String> buildLines() {
        Map<String, Object> raw = node.raw();
        List<String> out = new ArrayList<>();
        out.add("Knoten " + node.sender() + "@" + node.host() + "   RTT " + node.rttMs() + "ms");

        section(out, "JVM / Mem");
        kvBytes(out, raw, "Heap used", "jvm.heap.used");
        kvBytes(out, raw, "Heap committed", "jvm.heap.committed");
        kvBytes(out, raw, "Heap max", "jvm.heap.max");
        kvBytes(out, raw, "Nonheap used", "jvm.nonheap.used");
        kvBytes(out, raw, "Free mem", "jvm.free_mem");
        kvBytes(out, raw, "Total mem", "jvm.total_mem");
        kvBytes(out, raw, "Max mem", "jvm.max_mem");
        kv(out, raw, "JVM version", "jvm.version");

        section(out, "Threads");
        kv(out, raw, "Active", "jvm.threads.active");
        kv(out, raw, "Peak", "jvm.threads.peak");
        kv(out, raw, "Daemons", "jvm.threads.deamons");
        kv(out, raw, "Total started", "jvm.threads.total_started");

        Map<String, Object> cache = asMap(raw.get("morphium.cachestats"));
        if (cache != null) {
            section(out, "Cache");
            kvPct(out, cache, "Hit%", "CHITSPERC");
            kvPct(out, cache, "Miss%", "CMISSPERC");
            kv(out, cache, "Reads", "READS");
            kv(out, cache, "Writes", "WRITES");
            kv(out, cache, "Writes cached", "WRITES_CACHED");
            kv(out, cache, "Cache entries", "CACHE_ENTRIES");
            kv(out, cache, "Write-buffer entries", "WRITE_BUFFER_ENTRIES");
        }

        Map<String, Object> drv = asMap(raw.get("morphium.driver.stats"));
        if (drv != null) {
            section(out, "Driver");
            kv(out, drv, "Connections in use", "CONNECTIONS_IN_USE");
            kv(out, drv, "Connections in pool", "CONNECTIONS_IN_POOL");
            kv(out, drv, "Connections opened", "CONNECTIONS_OPENED");
            kv(out, drv, "Connections closed", "CONNECTIONS_CLOSED");
            kv(out, drv, "Errors", "ERRORS");
            kv(out, drv, "Failovers", "FAILOVERS");
            kv(out, drv, "Msg sent", "MSG_SENT");
            kv(out, drv, "Reply received", "REPLY_RECEIVED");
        }
        Map<String, Object> conns = asMap(raw.get("morphium.driver.connections"));
        if (conns != null && !conns.isEmpty()) {
            out.add("  Connections per host:");
            for (var e : new TreeMap<>(conns).entrySet()) out.add("    " + e.getKey() + ": " + e.getValue());
        }

        section(out, "Messaging");
        kv(out, raw, "In progress", "messaging.in_progress");
        kv(out, raw, "Processing", "messaging.processing");
        kv(out, raw, "Waiting for answers", "messaging.waiting_for_answers");
        kv(out, raw, "Window size", "messaging.window_size");
        kv(out, raw, "Pause", "messaging.pause");
        kv(out, raw, "Changestream", "messaging.changestream");
        kv(out, raw, "Multithreaded", "messaging.multithreadded");
        kv(out, raw, "Trip (server) ms", "messaging.time_till_recieved");
        Map<String, Object> topics = asMap(raw.get("message_listeners_by_name"));
        if (topics != null && !topics.isEmpty()) {
            out.add("  Topics: " + String.join(", ", new TreeMap<>(topics).keySet()));
        }

        Object repl = raw.get("morphium.driver.replicaset_status");
        if (repl != null) {
            section(out, "ReplicaSet");
            out.add("  " + repl);
        }

        Map<String, Object> cfg = asMap(raw.get("morphium.config"));
        if (cfg != null && !cfg.isEmpty()) {
            section(out, "Config");
            for (var e : new TreeMap<>(cfg).entrySet()) out.add("  " + e.getKey() + " = " + e.getValue());
        }
        return out;
    }

    private void section(List<String> out, String title) {
        out.add("");
        out.add("── " + title + " ──");
    }

    private void kv(List<String> out, Map<String, Object> m, String label, String key) {
        if (m.containsKey(key)) out.add(String.format("  %-22s %s", label, String.valueOf(m.get(key))));
    }

    private void kvBytes(List<String> out, Map<String, Object> m, String label, String key) {
        if (m.get(key) instanceof Number n) out.add(String.format("  %-22s %s", label, StatusFormat.humanBytes(n.longValue())));
    }

    private void kvPct(List<String> out, Map<String, Object> m, String label, String key) {
        if (m.get(key) instanceof Number n) out.add(String.format("  %-22s %s", label, StatusFormat.pct(n.doubleValue())));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        if (max <= 1) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

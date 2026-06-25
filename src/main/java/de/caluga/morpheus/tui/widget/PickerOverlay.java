package de.caluga.morpheus.tui.widget;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Centered multi-select overlay: type to search, ↑/↓ move, space toggles the current item,
 *  Tab toggles all current matches, backspace edits the query, Enter commits, Esc cancels.
 *  Pure widget — strings in, Set<String> out; no domain knowledge. */
public class PickerOverlay {

    public enum Outcome { STAY, COMMIT, CANCEL }

    private final String title;
    private final List<String> items;
    private final Map<String, String> labels;
    private final Set<String> selection = new LinkedHashSet<>();
    private String query = "";
    private int cursor = 0;

    public PickerOverlay(String title, List<String> items, Map<String, String> labels, Set<String> initial) {
        this.title = title;
        this.items = new ArrayList<>(items);
        this.labels = labels;
        this.selection.addAll(initial);
    }

    public Set<String> selection() { return selection; }
    String labelOf(String id) { return labels.getOrDefault(id, id); }

    /** Items matching the current query (case-insensitive substring on the label). */
    List<String> visibleItems() {
        if (query.isEmpty()) return items;
        String q = query.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String it : items) {
            if (labels.getOrDefault(it, it).toLowerCase().contains(q)) out.add(it);
        }
        return out;
    }

    public Outcome onKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) return Outcome.CANCEL;
        if (key.getKeyType() == KeyType.Enter) return Outcome.COMMIT;
        List<String> vis = visibleItems();
        if (key.getKeyType() == KeyType.ArrowUp) { if (cursor > 0) cursor--; return Outcome.STAY; }
        if (key.getKeyType() == KeyType.ArrowDown) { if (cursor < vis.size() - 1) cursor++; return Outcome.STAY; }
        if (key.getKeyType() == KeyType.Backspace) {
            if (!query.isEmpty()) { query = query.substring(0, query.length() - 1); cursor = 0; }
            return Outcome.STAY;
        }
        if (key.getKeyType() == KeyType.Tab) {
            boolean allSelected = !vis.isEmpty() && selection.containsAll(vis);
            for (String it : vis) { if (allSelected) selection.remove(it); else selection.add(it); }
            return Outcome.STAY;
        }
        Character ch = key.getCharacter();
        if (ch != null) {
            if (ch == ' ') {
                if (cursor < vis.size()) {
                    String item = vis.get(cursor);
                    if (!selection.remove(item)) selection.add(item);
                }
            } else if (!Character.isISOControl(ch)) {
                query += ch;
                cursor = 0;
            }
        }
        return Outcome.STAY;
    }

    public void draw(TextGraphics g) {
        int width = g.getSize().getColumns();
        int height = g.getSize().getRows();
        List<String> vis = visibleItems();
        int contentW = ("Suche: " + query).length() + 4;
        for (String it : vis) contentW = Math.max(contentW, labels.getOrDefault(it, it).length() + 6);
        int boxW = Math.min(width - 6, Math.max(28, contentW));
        int rows = Math.min(Math.max(1, vis.size()), Math.max(3, height - 10));
        int boxH = rows + 4;
        int x0 = (width - boxW) / 2;
        int y0 = (height - boxH) / 2;

        for (int y = y0; y < y0 + boxH; y++) {
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
            g.putString(x0, y, " ".repeat(Math.max(0, boxW)));
        }
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(x0, y0, trunc("┌─ " + title + " (" + vis.size() + ") " + "─".repeat(Math.max(0, boxW)), boxW));
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(x0 + 1, y0 + 1, trunc("Suche: " + query + "▌", boxW - 2));

        if (vis.isEmpty()) {
            g.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
            g.putString(x0 + 2, y0 + 3, trunc(items.isEmpty() ? "(noch nichts entdeckt)" : "(keine Treffer)", boxW - 4));
        } else {
            int first = Math.max(0, Math.min(cursor - rows / 2, vis.size() - rows));
            for (int i = 0; i < rows; i++) {
                int idx = first + i;
                if (idx >= vis.size()) break;
                String item = vis.get(idx);
                boolean sel = selection.contains(item);
                boolean cur = idx == cursor;
                String line = (cur ? "▸ " : "  ") + (sel ? "[x] " : "[ ] ") + labels.getOrDefault(item, item);
                g.setForegroundColor(cur ? TextColor.ANSI.GREEN_BRIGHT : TextColor.ANSI.DEFAULT);
                g.putString(x0 + 1, y0 + 2 + i, trunc(line, boxW - 2));
            }
        }
        g.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
        g.putString(x0 + 1, y0 + boxH - 1, trunc("tippen=suche  ↑↓  space  tab=alle  enter  esc", boxW - 2));
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        if (max <= 1) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

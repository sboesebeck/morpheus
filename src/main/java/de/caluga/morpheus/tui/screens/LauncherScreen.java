package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.config.ConnectionSpec;
import de.caluga.morpheus.config.ConnectionStore;
import de.caluga.morpheus.tui.Screen;
import de.caluga.morpheus.tui.widget.ListBox;
import de.caluga.morpheus.tui.widget.ThemeStyle;

import java.util.ArrayList;
import java.util.List;

/** Hybrid launcher: pick a connection (left) and a start view (right). */
public class LauncherScreen implements Screen {

    public enum Column { CONNECTIONS, VIEWS }

    private static final List<String> VIEWS = List.of("messages", "nodes", "status", "graph (Phase 3)");

    private final MorpheusContext ctx;
    private final ConnectionStore store;
    private final ListBox<String> connections;
    private final ListBox<String> views = new ListBox<>(VIEWS);
    private Column active = Column.CONNECTIONS;

    public LauncherScreen(MorpheusContext ctx) {
        this.ctx = ctx;
        this.store = new ConnectionStore(ctx.getConfig());
        this.connections = new ListBox<>(new ArrayList<>(store.list()));
    }

    // ── test seams ──
    public String selectedConnection() { return connections.selected(); }
    public String selectedView()       { return views.selected(); }
    public Column activeColumn()        { return active; }

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) return Result.stay();
        if (key.getCharacter() != null && key.getCharacter() == 'q') return Result.quit();

        switch (key.getKeyType()) {
            case Tab -> active = (active == Column.CONNECTIONS) ? Column.VIEWS : Column.CONNECTIONS;
            case ArrowUp ->   (active == Column.CONNECTIONS ? connections : views).up();
            case ArrowDown -> (active == Column.CONNECTIONS ? connections : views).down();
            case Enter -> { /* Task 11 pushes the chosen view here */ return Result.stay(); }
            default -> { /* a/e/d/t wired in Tasks 8-9 */ }
        }
        return Result.stay();
    }

    @Override
    public void draw(TextGraphics g) {
        g.setForegroundColor(ThemeStyle.color("c3"));
        g.putString(2, 0, "M O R P H E U S  ·  Messaging Monitor");
        g.setForegroundColor(TextColor.ANSI.DEFAULT);

        // Connection column
        g.putString(2, 2, "Verbindungen");
        drawList(g, connections, 2, 3, active == Column.CONNECTIONS);

        // View column
        int viewCol = 30;
        g.putString(viewCol, 2, "Start-Ansicht");
        drawList(g, views, viewCol, 3, active == Column.VIEWS);

        // Details of highlighted connection
        String sel = connections.selected();
        if (sel != null) {
            ConnectionSpec spec = store.load(sel);
            int dy = 4 + Math.max(connections.items().size(), VIEWS.size());
            g.putString(2, dy, "Hosts: " + String.join(",", spec.hosts()));
            g.putString(2, dy + 1, "DB: " + spec.database());
            g.putString(2, dy + 2, "Proxy: " + (spec.proxyEnabled()
                    ? spec.proxyHost() + ":" + spec.proxyPort() + " ✓" : "aus"));
        }

        // Footer
        g.setForegroundColor(TextColor.ANSI.WHITE);
        g.putString(2, g.getSize().getRows() - 1,
                "[⏎] starten  [a] neu  [e] edit  [d] löschen  [t] test  [q] quit");
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
    }

    private void drawList(TextGraphics g, ListBox<String> box, int x, int y, boolean activeCol) {
        List<String> items = box.items();
        for (int i = 0; i < items.size(); i++) {
            boolean sel = i == box.selectedIndex();
            String marker = sel ? "▶ " : "  ";
            if (sel && activeCol) g.setForegroundColor(ThemeStyle.color("c3"));
            else g.setForegroundColor(TextColor.ANSI.DEFAULT);
            g.putString(x, y + i, marker + items.get(i));
        }
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
    }
}

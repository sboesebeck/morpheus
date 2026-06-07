package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.config.ConnectionSpec;
import de.caluga.morpheus.config.ConnectionStore;
import de.caluga.morpheus.tui.ConnectionTester;
import de.caluga.morpheus.tui.Screen;
import de.caluga.morpheus.tui.widget.ListBox;
import de.caluga.morpheus.tui.widget.ThemeStyle;

import java.util.ArrayList;
import java.util.List;

/** Hybrid launcher: pick a connection (left) and a start view (right). */
public class LauncherScreen implements Screen {

    public enum Column { CONNECTIONS, VIEWS }

    private static final List<String> VIEWS = List.of("messages", "topics", "nodes", "status", "graph (Phase 3)");

    private final MorpheusContext ctx;
    private final ConnectionStore store;
    private final ListBox<String> connections;
    private final ListBox<String> views = new ListBox<>(VIEWS);
    private Column active = Column.CONNECTIONS;
    private ConnectionTester tester;
    private String testedConnection;
    private int lastConnCount = -1;

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
            case Enter -> {
                if (active == Column.VIEWS && connections.selected() != null) {
                    if (views.selected().startsWith("graph")) return Result.stay(); // disabled in Phase 2
                    // Fresh context per view so switching connections actually reconnects
                    // (MorpheusContext.connect() is idempotent and would reuse the first one).
                    de.caluga.morpheus.config.ConfigurationManager cm =
                        new de.caluga.morpheus.config.ConfigurationManager();
                    cm.setConnectionOverride(connections.selected());
                    MorpheusContext viewCtx = new MorpheusContext(cm);
                    try {
                        viewCtx.connect();
                    } catch (Exception ex) {
                        return Result.stay();
                    }
                    Screen view = viewFor(views.selected(), viewCtx);
                    return view == null ? Result.stay() : Result.push(view);
                }
            }
            case Character -> {
                char c = key.getCharacter();
                if (c == 't' && active == Column.CONNECTIONS && connections.selected() != null) {
                    testedConnection = connections.selected();
                    tester = new ConnectionTester(this::probe, 5000);
                    tester.start(testedConnection);
                }
                if (c == 'a' && active == Column.CONNECTIONS) {
                    return Result.push(new ConnectionFormScreen(ctx, null));
                }
                if (c == 'e' && active == Column.CONNECTIONS && connections.selected() != null) {
                    return Result.push(new ConnectionFormScreen(ctx, store.load(connections.selected())));
                }
                if (c == 'd' && active == Column.CONNECTIONS && connections.selected() != null) {
                    return Result.push(new ConfirmDeleteScreen(ctx, connections.selected()));
                }
            }
            default -> { }
        }
        return Result.stay();
    }

    @Override
    public void draw(TextGraphics g) {
        java.util.List<String> names = new java.util.ArrayList<>(store.list());
        if (names.size() != lastConnCount) {
            connections.setItems(names);
            lastConnCount = names.size();
        }

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

        // Test result line
        if (tester != null && testedConnection != null) {
            int ty = 4 + Math.max(connections.items().size(), VIEWS.size()) + 4;
            g.putString(2, ty, "Test " + testedConnection + ": " + tester.status());
        }

        // Footer
        g.setForegroundColor(TextColor.ANSI.WHITE);
        g.putString(2, g.getSize().getRows() - 1,
                "[⏎] starten  [a] neu  [e] edit  [d] löschen  [t] test  [q] quit");
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
    }

    /** Builds the connection and sends one status PING; returns a short result line. */
    private String probe(String connectionName) throws Exception {
        // Fresh config (default path) so the test never mutates the launcher's shared
        // ConfigurationManager from a background thread.
        de.caluga.morpheus.config.ConfigurationManager cm = new de.caluga.morpheus.config.ConfigurationManager();
        cm.setConnectionOverride(connectionName);
        de.caluga.morphium.Morphium m = null;
        try {
            var factory = new de.caluga.morpheus.connection.MorphiumConnectionFactory(cm, ctx.getTheme());
            m = factory.createMorphium();
            var messaging = factory.createMessaging(m);
            var msg = new de.caluga.morphium.messaging.Msg(
                    messaging.getStatusInfoListenerName(), "ALL", "PING", 3000);
            msg.setMsgId(new de.caluga.morphium.driver.MorphiumId());
            long t0 = System.currentTimeMillis();
            var answers = messaging.sendAndAwaitAnswers(msg, 1000, 3000);
            long ms = System.currentTimeMillis() - t0;
            return "● " + answers.size() + " nodes, " + ms + "ms";
        } finally {
            if (m != null) m.close();
        }
    }

    /** Maps a view name to its screen using the given (already connected) context; null = disabled. */
    Screen viewFor(String viewName, MorpheusContext viewCtx) {
        return switch (viewName) {
            case "messages" -> new MessagesScreen(viewCtx);
            case "topics" -> new TopicsScreen(viewCtx);
            case "nodes" -> new NodesScreen(viewCtx);
            case "status" -> new StatusScreen(viewCtx);
            default -> null; // "graph (Phase 3)"
        };
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

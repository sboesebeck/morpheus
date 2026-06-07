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
import de.caluga.morpheus.tui.widget.Banner;
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
                    if (views.selected().startsWith("graph")) return Result.stay(); // disabled
                    return Result.push(new ConnectingScreen(
                            connections.selected(), views.selected(), LauncherScreen::viewFor));
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

        // Banner + tagline
        Banner.draw(g, 2, 0); // rows 0..4
        g.setForegroundColor(TextColor.ANSI.WHITE_BRIGHT);
        g.putString(2, Banner.LINES.length, "Messaging Monitor · v" + de.caluga.morpheus.Version.VERSION);
        g.setForegroundColor(TextColor.ANSI.DEFAULT);

        int top = Banner.LINES.length + 2; // columns start below banner+tagline

        // Connection column
        g.setForegroundColor(ThemeStyle.color("c3"));
        g.putString(2, top, "Verbindungen");
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        drawList(g, connections, 2, top + 1, active == Column.CONNECTIONS);

        // View column
        int viewCol = 30;
        g.setForegroundColor(ThemeStyle.color("c3"));
        g.putString(viewCol, top, "Start-Ansicht");
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        drawList(g, views, viewCol, top + 1, active == Column.VIEWS);

        // Details of highlighted connection
        String sel = connections.selected();
        int dy = top + 2 + Math.max(connections.items().size(), VIEWS.size());
        if (sel != null) {
            ConnectionSpec spec = store.load(sel);
            g.setForegroundColor(TextColor.ANSI.WHITE);
            g.putString(2, dy, "Hosts: ");
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
            g.putString(9, dy, String.join(",", spec.hosts()));
            g.setForegroundColor(TextColor.ANSI.WHITE);
            g.putString(2, dy + 1, "DB: ");
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
            g.putString(6, dy + 1, spec.database());
            g.setForegroundColor(TextColor.ANSI.WHITE);
            g.putString(2, dy + 2, "Proxy: ");
            if (spec.proxyEnabled()) {
                g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
                g.putString(9, dy + 2, spec.proxyHost() + ":" + spec.proxyPort() + " ✓");
            } else {
                g.setForegroundColor(TextColor.ANSI.DEFAULT);
                g.putString(9, dy + 2, "aus");
            }
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
        }

        // Test result line
        if (tester != null && testedConnection != null) {
            g.putString(2, dy + 4, "Test " + testedConnection + ": " + tester.status());
        }

        // Footer (colored keys)
        int fy = g.getSize().getRows() - 1;
        g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
        g.putString(2, fy, "[⏎] starten  ");
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(15, fy, "[a] neu  [e] edit  [d] löschen  [t] test  [q] quit");
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
    static Screen viewFor(String viewName, MorpheusContext viewCtx) {
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
            if (sel && activeCol) g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
            else if (sel) g.setForegroundColor(ThemeStyle.color("c3"));
            else g.setForegroundColor(TextColor.ANSI.DEFAULT);
            g.putString(x, y + i, marker + items.get(i));
        }
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
    }
}

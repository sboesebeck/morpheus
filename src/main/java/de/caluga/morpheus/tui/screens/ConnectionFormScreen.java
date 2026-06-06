package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.config.ConnectionSpec;
import de.caluga.morpheus.config.ConnectionStore;
import de.caluga.morpheus.tui.Screen;
import de.caluga.morpheus.tui.widget.Field;

import java.util.ArrayList;
import java.util.List;

/** Add/edit a connection. Tab/Shift-Tab + Up/Down move between fields; Enter saves; Esc cancels. */
public class ConnectionFormScreen implements Screen {

    private final MorpheusContext ctx;
    private final List<Field> fields = new ArrayList<>();
    private final Field nameF, hostsF, dbF, authF, userF, pwF, queueF, proxyHostF, proxyPortF;
    private String messagingImpl;
    private boolean proxyEnabled;
    private int focus = 0;

    public ConnectionFormScreen(MorpheusContext ctx, ConnectionSpec existing) {
        this.ctx = ctx;
        ConnectionSpec e = existing != null ? existing
                : new ConnectionSpec("", List.of("localhost:27017"), "test", "admin",
                        "", "", "single", "msg", false, "127.0.0.1", 5555);
        nameF = add(new Field("Name", e.name(), Field.Type.TEXT));
        hostsF = add(new Field("Hosts", String.join(",", e.hosts()), Field.Type.TEXT));
        dbF = add(new Field("Datenbank", e.database(), Field.Type.TEXT));
        authF = add(new Field("Auth-DB", e.authDb(), Field.Type.TEXT));
        userF = add(new Field("User", e.user(), Field.Type.TEXT));
        pwF = add(new Field("Passwort", e.password(), Field.Type.PASSWORD));
        queueF = add(new Field("Queue", e.queueName(), Field.Type.TEXT));
        proxyHostF = add(new Field("Proxy-Host", e.proxyHost(), Field.Type.TEXT));
        proxyPortF = add(new Field("Proxy-Port", String.valueOf(e.proxyPort()), Field.Type.TEXT));
        messagingImpl = e.messagingImpl();
        proxyEnabled = e.proxyEnabled();
    }

    private Field add(Field f) { fields.add(f); return f; }

    // ── test seams ──
    public int focusIndex() { return focus; }

    public ConnectionSpec toSpec() {
        List<String> hosts = new ArrayList<>();
        for (String h : hostsF.value().split(",")) if (!h.trim().isEmpty()) hosts.add(h.trim());
        int port;
        try { port = Integer.parseInt(proxyPortF.value().trim()); } catch (Exception ex) { port = 5555; }
        return new ConnectionSpec(nameF.value().trim(), hosts, dbF.value(), authF.value(),
                userF.value(), pwF.value(), messagingImpl, queueF.value(),
                proxyEnabled, proxyHostF.value(), port);
    }

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) return Result.stay();
        switch (key.getKeyType()) {
            case Escape -> { return Result.pop(); }
            case Enter -> {
                try {
                    new ConnectionStore(ctx.getConfig()).save(toSpec());
                } catch (Exception ex) {
                    // stay on the form; a fuller error surface comes with the status line later
                    return Result.stay();
                }
                return Result.pop();
            }
            case Tab, ArrowDown -> focus = Math.min(focus + 1, fields.size() + 1); // +2 virtual rows: messaging, proxy
            case ReverseTab, ArrowUp -> focus = Math.max(focus - 1, 0);
            case Character -> {
                char c = key.getCharacter();
                if (focus == messagingRow() && c == ' ') {
                    messagingImpl = messagingImpl.equals("single") ? "multi" : "single";
                } else if (focus == proxyRow() && c == ' ') {
                    proxyEnabled = !proxyEnabled;
                } else if (focus < fields.size()) {
                    fields.get(focus).type(c);
                }
            }
            case Backspace -> { if (focus < fields.size()) fields.get(focus).backspace(); }
            default -> { }
        }
        return Result.stay();
    }

    private int messagingRow() { return fields.size(); }     // virtual row after the text fields
    private int proxyRow()     { return fields.size() + 1; } // virtual row after messaging

    @Override
    public void draw(TextGraphics g) {
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 0, nameF.value().isEmpty() ? "Neue Verbindung" : "Verbindung bearbeiten: " + nameF.value());
        g.setForegroundColor(TextColor.ANSI.DEFAULT);

        int y = 2;
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            if (i == focus) g.setForegroundColor(TextColor.ANSI.CYAN);
            g.putString(2, y + i, String.format("%-12s [%s]", f.label(), f.display()));
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
        }
        int my = y + fields.size();
        if (focus == messagingRow()) g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, my, "Messaging    (" + (messagingImpl.equals("single") ? "•" : " ") + ") single  ("
                + (messagingImpl.equals("multi") ? "•" : " ") + ") multi   [Space]");
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        if (focus == proxyRow()) g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, my + 1, "Proxy        [" + (proxyEnabled ? "x" : " ") + "] aktiv   [Space]");
        g.setForegroundColor(TextColor.ANSI.DEFAULT);

        g.putString(2, my + 3, "[⏎] speichern   [esc] abbrechen   [↹] nächstes Feld");
    }
}

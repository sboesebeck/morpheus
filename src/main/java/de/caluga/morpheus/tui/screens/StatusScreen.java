package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.core.StatusPinger;
import de.caluga.morpheus.tui.Screen;

import java.util.List;

/** Active-ping node roster: each live node's host, RTT, and subscribed topics. */
public class StatusScreen implements Screen {

    private final MorpheusContext ownedCtx;     // null in the test seam
    private final StatusPinger pinger;          // null in the test seam
    private volatile List<StatusPinger.NodeEntry> roster;
    private volatile boolean pinging = false;
    private volatile long lastPing = 0;

    public StatusScreen(MorpheusContext ctx) {
        this.ownedCtx = ctx;
        this.pinger = ctx.getMessaging() != null ? new StatusPinger(ctx.getMessaging()) : null;
        this.roster = List.of();
        refresh();
    }

    /** Test seam: render a fixed roster, no backend. */
    StatusScreen(List<StatusPinger.NodeEntry> roster) {
        this.ownedCtx = null;
        this.pinger = null;
        this.roster = roster;
    }

    @Override
    public void onClose() {
        if (ownedCtx != null) ownedCtx.close();
    }

    /** Starts one ping off-thread if none is in flight. */
    private void refresh() {
        if (pinger == null || pinging) return;
        pinging = true;
        Thread t = new Thread(() -> {
            try {
                List<StatusPinger.NodeEntry> r = pinger.ping(5000);
                if (!r.isEmpty() || roster.isEmpty()) roster = r;
            } catch (Throwable ignored) {
            } finally {
                pinging = false;
                lastPing = System.currentTimeMillis();
            }
        }, "status-ping");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) {
            // periodic auto-refresh ~every 10s (onKey is also reached via the poll loop on null)
            if (pinger != null && System.currentTimeMillis() - lastPing > 10_000) refresh();
            return Result.stay();
        }
        if (key.getCharacter() != null && key.getCharacter() == 'q') return Result.quit();
        if (key.getCharacter() != null && key.getCharacter() == 'r') { refresh(); return Result.stay(); }
        if (key.getKeyType() == KeyType.Escape) return Result.pop();
        return Result.stay();
    }

    @Override
    public void draw(TextGraphics g) {
        if (pinger != null && !pinging && System.currentTimeMillis() - lastPing > 10_000) {
            refresh();
        }
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 0, pinging ? "Knoten-Roster (Ping läuft…)" : "Knoten-Roster (Status-Ping)");
        g.putString(2, 1, String.format("%-26s %6s  %s", "Knoten (Sender@Host)", "RTT", "Abonnierte Topics"));
        g.setForegroundColor(TextColor.ANSI.DEFAULT);

        int row = 2;
        int maxRows = g.getSize().getRows() - 3;
        int n = 0;
        for (StatusPinger.NodeEntry e : roster) {
            if (n >= maxRows) break;
            n++;
            g.setForegroundColor(n % 2 == 0 ? TextColor.ANSI.WHITE : TextColor.ANSI.DEFAULT);
            String who = trunc(e.sender() + "@" + e.host(), 26);
            String topics = trunc(String.join(", ", e.topics()), Math.max(10, g.getSize().getColumns() - 40));
            g.putString(2, row++, String.format("%-26s %5dms  %s", who, e.rttMs(), topics));
        }
        if (n == 0) {
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
            g.putString(2, row, "(keine Antworten — [r] für erneuten Ping)");
        }
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, g.getSize().getRows() - 1, "[esc] zurück  [r] Ping  [q] quit");
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

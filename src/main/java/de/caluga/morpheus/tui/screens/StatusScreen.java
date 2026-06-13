package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.core.NodeStatus;
import de.caluga.morpheus.core.StatusFormat;
import de.caluga.morpheus.core.StatusPinger;
import de.caluga.morpheus.tui.Screen;

import java.util.List;

/** Top-like per-node status overview: live metrics per node, selectable, with a detail drill-down. */
public class StatusScreen implements Screen {

    private static final int[] TIMEOUT_PRESETS = {2000, 5000, 10000, 15000, 30000};

    private final MorpheusContext ownedCtx;   // null in the test seam
    private final StatusPinger pinger;        // null in the test seam
    private volatile List<NodeStatus> roster;
    private volatile boolean pinging = false;
    private volatile long lastPing = 0;
    private int selected = 0;
    private int pingTimeoutMs = 5000;

    public StatusScreen(MorpheusContext ctx) {
        this.ownedCtx = ctx;
        this.pinger = ctx.getMessaging() != null ? new StatusPinger(ctx.getMessaging()) : null;
        this.roster = List.of();
        refresh();
    }

    /** Test seam: render a fixed roster, no backend. */
    StatusScreen(List<NodeStatus> roster) {
        this.ownedCtx = null;
        this.pinger = null;
        this.roster = roster;
    }

    /** Test seams. */
    int currentTimeoutMs() { return pingTimeoutMs; }
    int selectedIndex() { return selected; }

    @Override
    public void onClose() {
        if (ownedCtx != null) ownedCtx.close();
    }

    private void refresh() {
        if (pinger == null || pinging) return;
        pinging = true;
        final int timeout = pingTimeoutMs;
        Thread t = new Thread(() -> {
            try {
                List<NodeStatus> r = pinger.ping(timeout);
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

    private void stepTimeout(int dir) {
        int idx = 0;
        for (int i = 0; i < TIMEOUT_PRESETS.length; i++) {
            if (TIMEOUT_PRESETS[i] == pingTimeoutMs) { idx = i; break; }
        }
        idx = Math.max(0, Math.min(TIMEOUT_PRESETS.length - 1, idx + dir));
        pingTimeoutMs = TIMEOUT_PRESETS[idx];
        refresh();
    }

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) {
            if (pinger != null && System.currentTimeMillis() - lastPing > 10_000) refresh();
            return Result.stay();
        }
        Character c = key.getCharacter();
        if (c != null && c == 'q') return Result.quit();
        if (c != null && c == 'r') { refresh(); return Result.stay(); }
        if (c != null && c == '+') { stepTimeout(+1); return Result.stay(); }
        if (c != null && c == '-') { stepTimeout(-1); return Result.stay(); }
        if (key.getKeyType() == KeyType.ArrowDown) { if (selected < roster.size() - 1) selected++; return Result.stay(); }
        if (key.getKeyType() == KeyType.ArrowUp) { if (selected > 0) selected--; return Result.stay(); }
        if (key.getKeyType() == KeyType.Enter) {
            if (!roster.isEmpty() && selected >= 0 && selected < roster.size()) {
                return Result.push(new NodeDetailScreen(roster.get(selected)));
            }
            return Result.stay();
        }
        if (key.getKeyType() == KeyType.Escape) return Result.pop();
        return Result.stay();
    }

    @Override
    public void draw(TextGraphics g) {
        if (pinger != null && !pinging && System.currentTimeMillis() - lastPing > 10_000) refresh();
        int width = g.getSize().getColumns();
        int height = g.getSize().getRows();

        if (selected >= roster.size()) selected = Math.max(0, roster.size() - 1);

        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 0, trunc("Knoten-Status (top)   Timeout: " + (pingTimeoutMs / 1000) + "s"
                + (pinging ? "   (Ping läuft…)" : ""), width - 3));

        int cNode = columnWidths(width);
        String header = String.format("%-" + cNode + "s %7s %13s %7s %11s %6s %9s %5s",
                "Sender@Host", "RTT", "Mem(u/max)", "Hit%", "Conn(u/p)", "Err", "Msg-sent", "Thr");
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 1, trunc(header, width - 3));

        int row = 2;
        int maxRows = height - 3;
        int n = 0;
        for (NodeStatus s : roster) {
            if (n >= maxRows) break;
            boolean sel = n == selected;
            n++;
            if (sel) g.setForegroundColor(TextColor.ANSI.GREEN_BRIGHT);
            else g.setForegroundColor(n % 2 == 0 ? TextColor.ANSI.WHITE : TextColor.ANSI.DEFAULT);
            String who = trunc((sel ? "▶ " : "  ") + s.sender() + "@" + s.host(), cNode);
            String mem = StatusFormat.humanBytes(s.heapUsed()) + "/" + StatusFormat.humanBytes(s.heapMax());
            String hit = StatusFormat.pct(s.cacheHitPct());
            String conn = (s.connInUse() == null ? "–" : s.connInUse()) + "/" + (s.connInPool() == null ? "–" : s.connInPool());
            String err = s.errors() == null ? "–" : String.valueOf(s.errors());
            String msg = s.msgSent() == null ? "–" : String.valueOf(s.msgSent());
            String thr = s.threadsActive() == null ? "–" : String.valueOf(s.threadsActive());
            String line = String.format("%-" + cNode + "s %5dms %13s %7s %11s %6s %9s %5s",
                    who, s.rttMs(), mem, hit, conn, err, msg, thr);
            g.putString(2, row++, trunc(line, width - 3));
        }
        if (n == 0) {
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
            g.putString(2, row, "(keine Antworten — [r] für erneuten Ping)");
        }
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, height - 1, "[↑/↓] Auswahl  [⏎] Detail  [+/-] Timeout  [r] Ping  [esc] zurück  [q] quit");
    }

    /** Width of the dynamic Sender@Host column. Fixed tail (metric columns + spaces) is 65 chars. */
    private int columnWidths(int width) {
        // tail: RTT(7)+Mem(13)+Hit(7)+Conn(11)+Err(6)+Msg(9)+Thr(5)=58, plus 7 single spaces, plus 3 margin
        return Math.max(18, width - 58 - 7 - 3);
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        if (max <= 1) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

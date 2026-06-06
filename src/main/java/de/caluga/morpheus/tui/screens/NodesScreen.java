package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.core.MessageFeed;
import de.caluga.morpheus.core.MessageStats;
import de.caluga.morpheus.core.MessageTracker;
import de.caluga.morpheus.tui.Screen;

/** Node activity list (top hosts/senders) from the core/ stats. */
public class NodesScreen implements Screen {

    private final MessageTracker tracker;
    private MorpheusContext ownedCtx;

    public NodesScreen(MorpheusContext ctx) {
        this.tracker = new MessageTracker(100);
        // Same guard as MessagesScreen: safe to construct with an unconnected context.
        if (ctx.getMorphium() != null) {
            this.ownedCtx = ctx;
            MessageFeed feed = new MessageFeed(ctx.getMorphium(), ctx.getMessaging(), tracker, () -> {}, false);
            Thread t = new Thread(() -> { try { feed.watch(); } catch (Throwable ignored) {} }, "nodes-feed");
            t.setDaemon(true);
            t.start();
        }
    }

    NodesScreen(MessageTracker tracker) { this.tracker = tracker; }

    @Override
    public void onClose() {
        if (ownedCtx != null) ownedCtx.close();
    }

    private boolean byHost = true;

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) return Result.stay();
        if (key.getCharacter() != null && key.getCharacter() == 'q') return Result.quit();
        if (key.getCharacter() != null && key.getCharacter() == 'v') { byHost = !byHost; return Result.stay(); }
        if (key.getKeyType() == KeyType.Escape) return Result.pop();
        return Result.stay();
    }

    @Override
    public void draw(TextGraphics g) {
        MessageStats stats = tracker.getStats();
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 0, "Verteilung: Sender → Antwortender  (" + (byHost ? "Hosts" : "Sender") + ")");
        g.putString(2, 1, String.format("%-44s %8s %8s", "Sender → Antwortender", "Anzahl", "Ø-RTT"));
        g.setForegroundColor(TextColor.ANSI.DEFAULT);

        int row = 2;
        int maxRows = g.getSize().getRows() - 3;
        int n = 0;
        for (MessageStats.PairCount p : stats.getTopPairs(byHost, maxRows)) {
            if (n >= maxRows) break;
            n++;
            g.setForegroundColor(n % 2 == 0 ? TextColor.ANSI.WHITE : TextColor.ANSI.DEFAULT);
            String pair = trunc(p.from() + " → " + p.to(), 44);
            g.putString(2, row++, String.format("%-44s %8d %7dms", pair, p.count(), p.avgRtt()));
        }
        if (n == 0) {
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
            g.putString(2, row, "(noch keine Antworten beobachtet)");
        }
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, g.getSize().getRows() - 1, "[esc] zurück  [v] Hosts/Sender  [q] quit");
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

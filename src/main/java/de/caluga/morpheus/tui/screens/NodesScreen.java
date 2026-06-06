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

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) return Result.stay();
        if (key.getCharacter() != null && key.getCharacter() == 'q') return Result.quit();
        if (key.getKeyType() == KeyType.Escape) return Result.pop();
        return Result.stay();
    }

    @Override
    public void draw(TextGraphics g) {
        MessageStats stats = tracker.getStats();
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 0, "Aktive Hosts");
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        int row = 1;
        for (var h : stats.getTopHosts(10)) {
            g.putString(2, row++, String.format("%-30s %d msg", h.name(), h.count()));
        }
        row++;
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, row++, "Aktive Sender");
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        for (var sdr : stats.getTopSenders(10)) {
            g.putString(2, row++, String.format("%-30s %d msg", sdr.name(), sdr.count()));
        }
        g.putString(2, g.getSize().getRows() - 1, "[esc] zurück  [q] quit");
    }
}

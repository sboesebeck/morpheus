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

/** Per-topic aggregate: messages, answers, avg RTT, timeouts. Observed from the stream. */
public class TopicsScreen implements Screen {

    private final MessageTracker tracker;
    private MorpheusContext ownedCtx;

    public TopicsScreen(MorpheusContext ctx) {
        this.tracker = new MessageTracker(500);
        if (ctx.getMorphium() != null) {
            this.ownedCtx = ctx;
            MessageFeed feed = new MessageFeed(ctx.getMorphium(), ctx.getMessaging(), tracker, () -> {}, false);
            Thread t = new Thread(() -> { try { feed.watch(); } catch (Throwable ignored) {} }, "topics-feed");
            t.setDaemon(true);
            t.start();
        }
    }

    TopicsScreen(MessageTracker tracker) { this.tracker = tracker; }

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
        g.putString(2, 0, String.format("%-34s %8s %8s %8s %8s", "Topic", "Nachr.", "Antw.", "Ø-RTT", "Timeout"));
        g.setForegroundColor(TextColor.ANSI.DEFAULT);

        int row = 2;
        int maxRows = g.getSize().getRows() - 3;
        int n = 0;
        for (MessageStats.TopicAggregate a : stats.getTopicStats(maxRows)) {
            if (n >= maxRows) break;
            n++;
            g.setForegroundColor(n % 2 == 0 ? TextColor.ANSI.WHITE : TextColor.ANSI.DEFAULT);
            g.putString(2, row++, String.format("%-34s %8d %8d %7dms %8d",
                    trunc(a.topic(), 34), a.messages(), a.answers(), a.avgRtt(), a.timeouts()));
        }
        if (n == 0) {
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
            g.putString(2, row, "(noch keine Nachrichten beobachtet)");
        }
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, g.getSize().getRows() - 1, "[esc] zurück  [q] quit");
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

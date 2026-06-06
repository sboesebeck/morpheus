package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.core.MessageFeed;
import de.caluga.morpheus.core.MessageInfo;
import de.caluga.morpheus.core.MessageStats;
import de.caluga.morpheus.core.MessageTracker;
import de.caluga.morpheus.tui.Screen;

import java.util.List;

/** Live message table rendered on Lanterna; same core/ data as the CLI monitor. */
public class MessagesScreen implements Screen {

    private final MessageTracker tracker;

    /** Production: connect a feed (ctx must already be connected by the launcher). */
    public MessagesScreen(MorpheusContext ctx) {
        this.tracker = new MessageTracker(100);
        // Guard so the screen can also be constructed with an unconnected context (unit tests,
        // disabled paths): only start the feed when there is a live Morphium.
        if (ctx.getMorphium() != null) {
            MessageFeed feed = new MessageFeed(ctx.getMorphium(), ctx.getMessaging(),
                    tracker, () -> {}, false);
            Thread t = new Thread(feed::watch, "messages-feed");
            t.setDaemon(true);
            t.start();
        }
    }

    /** Test seam: render a hand-fed tracker without a DB feed. */
    MessagesScreen(MessageTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) return Result.stay();
        if (key.getCharacter() != null && key.getCharacter() == 'q') return Result.quit();
        if (key.getKeyType() == com.googlecode.lanterna.input.KeyType.Escape) return Result.pop();
        return Result.stay();
    }

    @Override
    public void draw(TextGraphics g) {
        MessageStats stats = tracker.getStats();
        long uptime = (System.currentTimeMillis() - stats.getStartTime()) / 1000;
        g.setForegroundColor(TextColor.ANSI.YELLOW);
        g.putString(2, 0, String.format("Uptime: %ds | Messages: %d | Answers: %d | Timeouts: %d | Buffer: %d",
                uptime, stats.getTotalMessages(), stats.getTotalAnswers(),
                stats.getTotalTimeouts(), tracker.getBufferSize()));
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 2, String.format("%-4s %-8s %-22s %-22s %-8s", "#", "Time", "Sender", "Topic", "RTT"));
        g.setForegroundColor(TextColor.ANSI.DEFAULT);

        List<MessageInfo> messages = tracker.getMessagesNewestFirst();
        long now = System.currentTimeMillis();
        int row = 3;
        int maxRows = g.getSize().getRows() - 4;
        int n = 0;
        for (MessageInfo info : messages) {
            if (n >= maxRows) break;
            n++;
            String time = ((now - info.timestamp) / 1000) + "s";
            String rtt = info.rtt != null ? info.rtt + "ms" : "";
            if (info.isTimedOut) g.setForegroundColor(TextColor.ANSI.RED);
            else g.setForegroundColor(n % 2 == 0 ? TextColor.ANSI.WHITE : TextColor.ANSI.DEFAULT);
            g.putString(2, row++, String.format("%-4d %-8s %-22s %-22s %-8s",
                    n, time, trunc(info.sender, 22), trunc(info.topic, 22), rtt));
        }
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, g.getSize().getRows() - 1, "[esc] zurück  [q] quit");
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

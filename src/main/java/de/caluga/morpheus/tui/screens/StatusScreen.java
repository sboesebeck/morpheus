package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;

import java.util.ArrayList;
import java.util.List;

/** One-shot status PING result list. */
public class StatusScreen implements de.caluga.morpheus.tui.Screen {

    private final List<String> lines = new ArrayList<>();
    private volatile boolean done = false;

    public StatusScreen(MorpheusContext ctx) {
        Thread t = new Thread(() -> {
            try {
                var messaging = ctx.getMessaging();
                Msg msg = new Msg(messaging.getStatusInfoListenerName(), "ALL", "PING", 3000);
                msg.setMsgId(new MorphiumId());
                var answers = messaging.sendAndAwaitAnswers(msg, 1000, 3000);
                long t0 = System.currentTimeMillis();
                for (var a : answers) {
                    lines.add(a.getSender() + " @ " + a.getSenderHost()
                            + "  " + (a.getTimestamp() - t0) + "ms");
                }
                if (answers.isEmpty()) lines.add("Keine Antworten.");
            } catch (Exception e) {
                lines.add("Fehler: " + e.getMessage());
            } finally {
                done = true;
            }
        }, "status-ping");
        t.setDaemon(true);
        t.start();
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
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 0, done ? "Knoten-Status" : "Knoten-Status (warte auf Antworten…)");
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        int row = 2;
        for (String l : new ArrayList<>(lines)) {
            g.putString(2, row++, l);
        }
        g.putString(2, g.getSize().getRows() - 1, "[esc] zurück  [q] quit");
    }
}

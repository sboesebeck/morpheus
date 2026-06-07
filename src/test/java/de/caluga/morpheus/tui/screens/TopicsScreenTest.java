package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.core.MessageTracker;
import de.caluga.morpheus.tui.Screen;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TopicsScreenTest {

    private Msg req(String topic) {
        Msg m = new Msg(topic, "m", "v", 30000);
        m.setMsgId(new MorphiumId());
        m.setSender("s"); m.setSenderHost("h");
        m.setTimestamp(System.currentTimeMillis());
        return m;
    }

    @Test
    void escPops() {
        TopicsScreen s = new TopicsScreen(new MessageTracker(50));
        assertEquals(Screen.Result.Kind.POP, s.onKey(new KeyStroke(KeyType.Escape)).kind());
    }

    @Test
    void drawMarksTimeoutsOnOldUnansweredMessages() throws Exception {
        MessageTracker tracker = new MessageTracker(50);
        Msg old = req("orders");
        old.setTimestamp(System.currentTimeMillis() - 10_000);
        tracker.onInsert(old, "orders");

        TopicsScreen s = new TopicsScreen(tracker);
        com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal vt =
                new com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal();
        com.googlecode.lanterna.screen.TerminalScreen ts =
                new com.googlecode.lanterna.screen.TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics());
        ts.stopScreen();

        assertEquals(1, tracker.getStats().getTopicStats(10).get(0).timeouts(),
                "draw() must mark the old unanswered message as timed out");
    }

    @Test
    void rendersTopicRowsWithoutError() throws Exception {
        MessageTracker tracker = new MessageTracker(50);
        tracker.onInsert(req("order.created"), "order.created");
        tracker.onInsert(req("cache.sync"), "cache.sync");

        TopicsScreen s = new TopicsScreen(tracker);
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal();
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics());
        ts.stopScreen();
    }
}

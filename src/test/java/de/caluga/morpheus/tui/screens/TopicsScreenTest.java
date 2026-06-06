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

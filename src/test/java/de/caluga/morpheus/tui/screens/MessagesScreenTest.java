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

public class MessagesScreenTest {

    private Msg msg(String topic, String sender) {
        Msg m = new Msg(topic, "m", "v", 30000);
        m.setMsgId(new MorphiumId());
        m.setSender(sender);
        m.setSenderHost("host1");
        m.setTimestamp(System.currentTimeMillis());
        return m;
    }

    @Test
    void escPops() {
        MessageTracker tracker = new MessageTracker(50);
        MessagesScreen s = new MessagesScreen(tracker);
        assertEquals(Screen.Result.Kind.POP, s.onKey(new KeyStroke(KeyType.Escape)).kind());
    }

    @Test
    void rendersTrackerContentWithoutError() throws Exception {
        MessageTracker tracker = new MessageTracker(50);
        tracker.onInsert(msg("order.created", "svc-1"), "order.created");
        tracker.onInsert(msg("cache.sync", "svc-2"), "cache.sync");

        MessagesScreen s = new MessagesScreen(tracker);
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal();
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics()); // must not throw
        ts.stopScreen();
    }
}

package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.core.MessageTracker;
import de.caluga.morpheus.core.MessageFilter;
import de.caluga.morpheus.tui.Screen;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MessagesScreenTest {

    private Msg req(String topic, String sender, String host) {
        Msg m = new Msg(topic, "m", "v", 30000);
        m.setMsgId(new MorphiumId());
        m.setSender(sender); m.setSenderHost(host);
        m.setTimestamp(System.currentTimeMillis());
        return m;
    }

    private MessageTracker fed() {
        MessageTracker t = new MessageTracker(50);
        t.onInsert(req("orders", "a", "h1"), "orders");
        t.onInsert(req("cache", "b", "h2"), "cache");
        return t;
    }

    private void render(MessagesScreen s) throws Exception {
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new com.googlecode.lanterna.TerminalSize(160, 30));
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics());
        ts.stopScreen();
    }

    @Test
    void escPops() {
        MessagesScreen s = new MessagesScreen(new MessageTracker(10));
        assertEquals(Screen.Result.Kind.POP, s.onKey(new KeyStroke(KeyType.Escape)).kind());
    }

    @Test
    void topicKeyOpensPickerAndCommitFiltersRows() throws Exception {
        MessagesScreen s = new MessagesScreen(fed());
        render(s);
        assertEquals(2, s.visibleRowCount(30), "no filter → both rows");
        s.filter().topics.add("orders");      // simulate a committed picker selection
        assertEquals(1, s.visibleRowCount(30), "topic filter → one row");
    }

    @Test
    void tKeyOpensTopicPicker() {
        MessagesScreen s = new MessagesScreen(fed());
        // opening a picker is STAY; committing nothing then esc closes it
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('t', false, false)).kind());
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke(KeyType.Escape)).kind());
    }

    @Test
    void clearKeyResetsFilter() {
        MessagesScreen s = new MessagesScreen(fed());
        s.filter().topics.add("orders");
        s.onKey(new KeyStroke('c', false, false));
        assertTrue(s.filter().isEmpty());
    }

    @Test
    void rendersWithActiveFilterWithoutError() throws Exception {
        MessagesScreen s = new MessagesScreen(fed());
        s.filter().senders.add("a");
        s.filter().bodyRegex = "m";
        render(s);
    }
}

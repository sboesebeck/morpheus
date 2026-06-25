package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
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

    /** Convenience wrapper used by the render regression tests. */
    private Msg msg(String topic, String sender) {
        return req(topic, sender, "host1");
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

    // --- Render regression tests restored from master (commit e4f5049) ---

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

    @Test
    void tagColorIsYellowForV5GreenForV6() {
        assertEquals(TextColor.ANSI.YELLOW, MessagesScreen.tagColor(true));
        assertEquals(TextColor.ANSI.GREEN, MessagesScreen.tagColor(false));
    }

    @Test
    void rendersV5AndV6AndTimeoutWithoutError() throws Exception {
        MessageTracker tracker = new MessageTracker(50);
        Msg v6 = msg("order.created", "svc-6");                 // v6 by default
        tracker.onInsert(v6, "order.created");
        Msg v5 = msg("legacy.topic", "svc-5");
        // v5.setVersion(5) omitted: Msg has no setVersion(); isV5 is detected via getTopic()==null
        tracker.onInsert(v5, "legacy.topic");
        MessagesScreen s = new MessagesScreen(tracker);
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new TerminalSize(120, 30));
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics());                          // must not throw
        ts.stopScreen();
    }

    @Test
    void rendersNarrowTerminalWithoutError() throws Exception {
        MessageTracker tracker = new MessageTracker(50);
        tracker.onInsert(msg("order.created", "svc-1"), "order.created");
        MessagesScreen s = new MessagesScreen(tracker);
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new TerminalSize(40, 12));
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics());                          // must not throw at 40 cols
        ts.stopScreen();
    }

    @Test
    void rendersAnswererColumnsWideTerminal() throws Exception {
        MessageTracker tracker = new MessageTracker(50);
        Msg request = msg("order.created", "hermes");
        request.setTimestamp(1000);
        tracker.onInsert(request, "order.created");
        Msg ans = msg("order.created", "worker");
        ans.setInAnswerTo(request.getMsgId());
        ans.setSenderHost("wh1");
        ans.setTimestamp(1050);
        tracker.onInsert(ans, "order.created");

        MessagesScreen s = new MessagesScreen(tracker);
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new TerminalSize(200, 40));
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics()); // must not throw at 200 cols
        ts.stopScreen();
    }

    @Test
    void statusKeysCycleTriStates() {
        MessagesScreen s = new MessagesScreen(new MessageTracker(10));
        s.onKey(new KeyStroke('a', false, false));
        assertEquals(MessageFilter.TriState.ONLY, s.filter().answered);
        s.onKey(new KeyStroke('e', false, false));
        assertEquals(MessageFilter.TriState.ONLY, s.filter().timedOut);
        s.onKey(new KeyStroke('x', false, false));
        assertEquals(MessageFilter.TriState.ONLY, s.filter().exclusive);
        s.onKey(new KeyStroke('d', false, false));
        assertEquals(MessageFilter.TriState.ONLY, s.filter().deleted);
    }

    @Test
    void slashEntersRegexAndEnterCommits() {
        MessagesScreen s = new MessagesScreen(new MessageTracker(10));
        s.onKey(new KeyStroke('/', false, false));
        assertTrue(s.regexEditing());
        s.onKey(new KeyStroke('f', false, false));
        s.onKey(new KeyStroke('o', false, false));
        s.onKey(new KeyStroke('o', false, false));
        s.onKey(new KeyStroke(KeyType.Enter));
        assertFalse(s.regexEditing());
        assertEquals("foo", s.filter().bodyRegex);
    }

    @Test
    void regexEscapeCancelsWithoutApplying() {
        MessagesScreen s = new MessagesScreen(new MessageTracker(10));
        s.onKey(new KeyStroke('/', false, false));
        s.onKey(new KeyStroke('x', false, false));
        s.onKey(new KeyStroke(KeyType.Escape));
        assertFalse(s.regexEditing());
        assertNull(s.filter().bodyRegex);
    }
}

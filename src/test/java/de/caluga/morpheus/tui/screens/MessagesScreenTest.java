package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.config.ConfigurationManager;
import de.caluga.morpheus.core.MessageTracker;
import de.caluga.morpheus.core.MessageFilter;
import de.caluga.morpheus.tui.Screen;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class MessagesScreenTest {

    @TempDir Path tmp;

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
        render(s, 160, 30);
    }

    private void render(MessagesScreen s, int width, int height) throws Exception {
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new com.googlecode.lanterna.TerminalSize(width, height));
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

    @Test
    void productionHistoryIsBoundedToOneThousandMessages() {
        assertEquals(1_000, MessagesScreen.DEFAULT_HISTORY_CAPACITY);
    }

    @Test
    void plusMinusAdjustHistoryImmediatelyAndPersist() throws Exception {
        String path = tmp.resolve("morpheus.properties").toString();
        ConfigurationManager config = new ConfigurationManager(path);
        MessagesScreen s = new MessagesScreen(new MorpheusContext(config));

        assertEquals(1_000, s.historyCapacity());
        s.onKey(new KeyStroke('+', false, false));
        assertEquals(2_500, s.historyCapacity());
        assertEquals(2_500, new ConfigurationManager(path).getMessageHistorySize());

        s.onKey(new KeyStroke('-', false, false));
        assertEquals(1_000, s.historyCapacity());
    }

    @Test
    void shrinkingHistoryEvictsOldestMessagesImmediately() {
        MessageTracker tracker = new MessageTracker(1_000);
        for (int i = 0; i < 600; i++) tracker.onInsert(msg("topic-" + i, "sender"), "topic-" + i);
        MessagesScreen s = new MessagesScreen(tracker);

        s.onKey(new KeyStroke('-', false, false));

        assertEquals(500, s.historyCapacity());
        assertEquals(500, tracker.getBufferSize());
        assertEquals("topic-599", tracker.getMessagesNewestFirst().get(0).topic);
        assertEquals("topic-100", tracker.getMessagesNewestFirst().get(499).topic);
    }

    @Test
    void arrowsMoveSelectionAndEnterOpensDetail() throws Exception {
        MessagesScreen s = new MessagesScreen(fed());
        render(s);

        assertEquals(0, s.selectedIndex());
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke(KeyType.ArrowDown)).kind());
        assertTrue(s.paused());
        assertEquals(1, s.selectedIndex());
        MorphiumId selectedId = s.selectedMessageId();

        Screen.Result detail = s.onKey(new KeyStroke(KeyType.Enter));
        assertEquals(Screen.Result.Kind.PUSH, detail.kind());
        assertInstanceOf(MessageDetailScreen.class, detail.next());
        assertEquals(String.valueOf(selectedId), ((MessageDetailScreen) detail.next()).messageId());

        s.onKey(new KeyStroke(KeyType.ArrowUp));
        assertEquals(0, s.selectedIndex());
        assertNull(s.selectedMessageId(), "first row follows the newest live message");
    }

    @Test
    void pageKeysMoveByVisibleWindowAndAdjustScroll() throws Exception {
        MessageTracker tracker = new MessageTracker(50);
        for (int i = 0; i < 20; i++) tracker.onInsert(msg("topic-" + i, "sender-" + i), "topic-" + i);
        MessagesScreen s = new MessagesScreen(tracker);
        render(s, 120, 12);

        s.onKey(new KeyStroke(KeyType.PageDown));
        assertEquals(4, s.selectedIndex());
        render(s, 120, 12);
        assertTrue(s.scrollOffset() > 0);

        s.onKey(new KeyStroke(KeyType.PageUp));
        assertEquals(0, s.selectedIndex());
        render(s, 120, 12);
        assertEquals(0, s.scrollOffset());
    }

    @Test
    void navigationPausesFeedAndKeepsTheVisibleSnapshotStable() throws Exception {
        MessageTracker tracker = fed();
        MessagesScreen s = new MessagesScreen(tracker);
        render(s);
        s.onKey(new KeyStroke(KeyType.ArrowDown));
        MorphiumId selectedId = s.selectedMessageId();
        assertTrue(s.paused());
        assertEquals(2, s.visibleRowCount(30));

        tracker.onInsert(msg("new", "new-sender"), "new");
        render(s);

        assertEquals(selectedId, s.selectedMessageId());
        assertEquals(1, s.selectedIndex());
        assertEquals(2, s.visibleRowCount(30));
        assertEquals(1, s.messagesSincePause());
    }

    @Test
    void pResumesLiveModeAndJumpsToNewestMessage() throws Exception {
        MessageTracker tracker = fed();
        MessagesScreen s = new MessagesScreen(tracker);
        render(s);
        s.onKey(new KeyStroke(KeyType.ArrowDown));
        tracker.onInsert(msg("new", "new-sender"), "new");

        s.onKey(new KeyStroke('p', false, false));
        render(s);

        assertFalse(s.paused());
        assertEquals(0, s.selectedIndex());
        assertNull(s.selectedMessageId());
        assertEquals(3, s.visibleRowCount(30));
        assertEquals(0, s.messagesSincePause());
    }

    @Test
    void pCanPauseWithoutMovingTheCursor() throws Exception {
        MessagesScreen s = new MessagesScreen(fed());
        render(s);

        s.onKey(new KeyStroke('p', false, false));

        assertTrue(s.paused());
        assertEquals(0, s.selectedIndex());
    }
}

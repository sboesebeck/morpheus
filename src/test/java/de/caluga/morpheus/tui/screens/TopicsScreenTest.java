package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.core.MessageTracker;
import de.caluga.morpheus.core.NodeRegistry;
import de.caluga.morpheus.tui.Screen;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TopicsScreenTest {

    private Msg req(String topic) {
        Msg m = new Msg(topic, "m", "v", 30000);
        m.setMsgId(new MorphiumId());
        m.setSender("s"); m.setSenderHost("h");
        m.setTimestamp(System.currentTimeMillis());
        return m;
    }

    private Map<String, TopicsScreen.Row> rowsByTopic(TopicsScreen s) {
        Map<String, TopicsScreen.Row> m = new HashMap<>();
        for (TopicsScreen.Row r : s.buildRows()) m.put(r.topic(), r);
        return m;
    }

    @Test
    void escPops() {
        TopicsScreen s = new TopicsScreen(new MessageTracker(50), new NodeRegistry("self"));
        assertEquals(Screen.Result.Kind.POP, s.onKey(new KeyStroke(KeyType.Escape)).kind());
    }

    @Test
    void rendersTopicRowsWithoutError() throws Exception {
        MessageTracker tracker = new MessageTracker(50);
        tracker.onInsert(req("order.created"), "order.created");
        tracker.onInsert(req("cache.sync"), "cache.sync");
        TopicsScreen s = new TopicsScreen(tracker, new NodeRegistry("self"));
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal();
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics());
        ts.stopScreen();
    }

    @Test
    void buildRowsUnionsPingAndStreamTopics() {
        MessageTracker tracker = new MessageTracker(50);
        tracker.onInsert(req("busy.topic"), "busy.topic");           // stream traffic, no listener
        NodeRegistry reg = new NodeRegistry("self");
        reg.markListener("w1", "h1", "silent.topic", 1);            // subscribed, no traffic
        TopicsScreen s = new TopicsScreen(tracker, reg);
        Map<String, TopicsScreen.Row> by = rowsByTopic(s);
        assertTrue(by.containsKey("busy.topic"), "stream-only topic appears");
        assertTrue(by.containsKey("silent.topic"), "ping-only topic appears");
        assertEquals(0, by.get("silent.topic").messages());
        assertEquals(1, by.get("silent.topic").listeners());
        assertTrue(by.get("busy.topic").messages() > 0);
        assertEquals(0, by.get("busy.topic").listeners());
    }

    @Test
    void firingIntoVoidWhenTrafficButNoListener() {
        MessageTracker tracker = new MessageTracker(50);
        tracker.onInsert(req("busy.topic"), "busy.topic");
        tracker.onInsert(req("ok.topic"), "ok.topic");
        NodeRegistry reg = new NodeRegistry("self");
        reg.markListener("w1", "h1", "ok.topic", 1);
        TopicsScreen s = new TopicsScreen(tracker, reg);
        Map<String, TopicsScreen.Row> by = rowsByTopic(s);
        assertTrue(by.get("busy.topic").firingIntoVoid(), "traffic + 0 listeners → firing into void");
        assertFalse(by.get("ok.topic").firingIntoVoid(), "traffic + listener → fine");
    }

    @Test
    void intervalPresetsStepAndClamp() {
        TopicsScreen s = new TopicsScreen(new MessageTracker(10), new NodeRegistry("self"));
        assertEquals(10_000, s.currentIntervalMs());
        s.onKey(new KeyStroke('-', false, false));
        assertEquals(5_000, s.currentIntervalMs());
        s.onKey(new KeyStroke('-', false, false));
        assertEquals(5_000, s.currentIntervalMs());           // clamped low
        for (int i = 0; i < 5; i++) s.onKey(new KeyStroke('+', false, false));
        assertEquals(30_000, s.currentIntervalMs());          // clamped high
    }

    @Test
    void enterDrillsDownToTopicListeners() {
        MessageTracker tracker = new MessageTracker(10);
        NodeRegistry reg = new NodeRegistry("self");
        reg.markListener("w1", "host-1", "orders", 1);
        reg.markListener("w2", "host-2", "orders", 1);
        TopicsScreen s = new TopicsScreen(tracker, reg);
        s.buildRows();                                        // (orders is the only row → index 0)
        Screen.Result r = s.onKey(new KeyStroke(KeyType.Enter));
        assertEquals(Screen.Result.Kind.PUSH, r.kind());
        assertTrue(r.next() instanceof TopicDetailScreen);
        TopicDetailScreen d = (TopicDetailScreen) r.next();
        assertEquals("orders", d.topic());
        assertEquals(2, d.listeners().size());
    }

    @Test
    void arrowDownMovesSelectionWithinBounds() throws Exception {
        MessageTracker tracker = new MessageTracker(10);
        tracker.onInsert(req("a.topic"), "a.topic");
        tracker.onInsert(req("b.topic"), "b.topic");
        TopicsScreen s = new TopicsScreen(tracker, new NodeRegistry("self"));
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal();
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics());                         // render once so rowCount is known
        ts.stopScreen();
        assertEquals(0, s.selectedIndex());
        s.onKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(1, s.selectedIndex());
        s.onKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals(1, s.selectedIndex(), "clamped at the last row");
    }

    @Test
    void defaultSortIsMessagesDescending() {
        MessageTracker t = new MessageTracker(50);
        t.onInsert(req("low"), "low");
        t.onInsert(req("high"), "high");
        t.onInsert(req("high"), "high");                 // high = 2 messages
        TopicsScreen s = new TopicsScreen(t, new NodeRegistry("self"));
        assertEquals(TopicsScreen.SortKey.MESSAGES, s.sortKey());
        assertTrue(s.sortDescending());
        assertEquals("high", s.buildRows().get(0).topic());
    }

    @Test
    void sKeyCyclesSortColumnSShiftReversesDirection() {
        TopicsScreen s = new TopicsScreen(new MessageTracker(10), new NodeRegistry("self"));
        assertEquals(TopicsScreen.SortKey.MESSAGES, s.sortKey());
        s.onKey(new KeyStroke('s', false, false));
        assertEquals(TopicsScreen.SortKey.ANSWERS, s.sortKey());
        assertTrue(s.sortDescending());
        s.onKey(new KeyStroke('S', false, false));
        assertFalse(s.sortDescending());
    }

    @Test
    void cycleToTopicSortsAlphabetically() {
        MessageTracker t = new MessageTracker(50);
        t.onInsert(req("banana"), "banana");
        t.onInsert(req("apple"), "apple");
        TopicsScreen s = new TopicsScreen(t, new NodeRegistry("self"));
        for (int i = 0; i < 5; i++) s.onKey(new KeyStroke('s', false, false)); // MESSAGES→…→TOPIC
        assertEquals(TopicsScreen.SortKey.TOPIC, s.sortKey());
        s.onKey(new KeyStroke('S', false, false));                            // desc→asc
        assertEquals("apple", s.buildRows().get(0).topic());
    }
}

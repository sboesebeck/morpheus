package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.core.FlowDeriver;
import de.caluga.morpheus.core.MessageInfo;
import de.caluga.morpheus.core.MessageTracker;
import de.caluga.morpheus.core.NodeRegistry;
import de.caluga.morpheus.tui.Screen;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GraphScreenTest {

    @Test
    void escPopsAndPToggles() {
        GraphScreen s = new GraphScreen(new NodeRegistry("self"));
        assertEquals(Screen.Result.Kind.POP, s.onKey(new KeyStroke(KeyType.Escape)).kind());
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('p', false, false)).kind());
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('h', false, false)).kind());
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('b', false, false)).kind());   // pulse toggle
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('m', false, false)).kind());   // metrics toggle
        assertEquals(Screen.Result.Kind.QUIT, s.onKey(new KeyStroke('q', false, false)).kind());
    }

    @Test
    void pulseModeRendersWithoutError() throws Exception {
        NodeRegistry r = new NodeRegistry("self");
        r.observeSend("hermes", "h1", 1000);
        r.observeRecv("worker2", "h3", 1000);
        GraphScreen s = new GraphScreen(r);
        s.onKey(new KeyStroke('b', false, false));   // switch to pulse (line-flash) mode
        renderOk(s, 120, 40);
        renderOk(s, 40, 12);
    }

    @Test
    void rendersSeededRegistryNarrowAndWide() throws Exception {
        NodeRegistry r = new NodeRegistry("self");
        r.observeSend("hermes", "h1", 1000);
        // an unset sender id shows up as a long uuid -> must shorten without throwing
        r.observeRecv("550e8400-e29b-41d4-a716-446655440000", "worker-03.prod.genios.de", 1000);
        r.markListener("550e8400-e29b-41d4-a716-446655440000", "worker-03.prod.genios.de", "order.created", 1000);
        r.observeRecv("worker2", "h3", 1000);
        GraphScreen s = new GraphScreen(r);
        renderOk(s, 120, 40);
        s.onKey(new KeyStroke('h', false, false));   // toggle host lines on
        renderOk(s, 120, 40);
        renderOk(s, 28, 8);     // below the minimum -> hint, must not throw
    }

    @Test
    void rendersEmptyRegistryWithoutError() throws Exception {
        renderOk(new GraphScreen(new NodeRegistry("self")), 100, 30);
    }

    @Test
    void gReturnsStayAndUnsupportedStaysBraille() throws Exception {
        NodeRegistry r = new NodeRegistry("self");
        r.observeSend("hermes", "h1", 1000);
        GraphScreen s = new GraphScreen(r);
        StringBuilder sink = new StringBuilder();
        s.gfxOut = sink;
        s.gfxSupportedCheck = () -> false;          // terminal does NOT support kitty graphics
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('g', false, false)).kind());
        renderOk(s, 120, 40);
        assertEquals(0, sink.length(), "unsupported terminal must emit no graphics escapes");
    }

    @Test
    void gOnSupportedTerminalEmitsImageAndToggleOffDeletes() throws Exception {
        NodeRegistry r = new NodeRegistry("self");
        r.observeSend("hermes", "h1", 1000);
        r.observeRecv("worker1", "h2", 1000);
        GraphScreen s = new GraphScreen(r);
        StringBuilder sink = new StringBuilder();
        s.gfxOut = sink;
        s.gfxSupportedCheck = () -> true;
        s.onKey(new KeyStroke('g', false, false));  // enable gfx
        renderOk(s, 120, 40);
        assertTrue(sink.toString().contains("a=T,f=24,o=z,"), "gfx mode emits a kitty image");

        sink.setLength(0);
        s.onKey(new KeyStroke('g', false, false));  // toggle off
        assertTrue(sink.toString().contains("\033_Ga=d,d=I,i=1"), "toggle-off deletes the image");
    }

    @Test
    void onCloseDeletesImageWhenGfxOn() {
        NodeRegistry r = new NodeRegistry("self");
        GraphScreen s = new GraphScreen(r);
        StringBuilder sink = new StringBuilder();
        s.gfxOut = sink;
        s.gfxSupportedCheck = () -> true;
        s.onKey(new KeyStroke('g', false, false));  // enable gfx
        s.onClose();
        assertTrue(sink.toString().contains("\033_Ga=d,d=I,i=1"), "onClose removes the image in gfx mode");
    }

    @Test
    void clearAndCapKeysReturnStay() {
        GraphScreen s = new GraphScreen(new NodeRegistry("self"));
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('c', false, false)).kind());   // clear filter
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('+', false, false)).kind());   // cap up
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('-', false, false)).kind());   // cap down
    }

    @Test
    void capPresetsStepAndClamp() {
        GraphScreen s = new GraphScreen(new NodeRegistry("self"));
        assertEquals(20, s.shotCap());                 // default preset
        s.onKey(new KeyStroke('-', false, false));
        assertEquals(10, s.shotCap());                 // one down
        s.onKey(new KeyStroke('-', false, false));
        assertEquals(10, s.shotCap());                 // clamped at the low end
        for (int i = 0; i < 9; i++) s.onKey(new KeyStroke('+', false, false));
        assertEquals(160, s.shotCap());                // clamped at the high end
    }

    @Test
    void clearKeyEmptiesFilter() {
        GraphScreen s = new GraphScreen(new NodeRegistry("self"));
        s.filter.topics.add("orders");
        s.filter.nodes.add("hermes");
        s.onKey(new KeyStroke('c', false, false));
        assertTrue(s.filter.isEmpty());
    }

    @Test
    void rendersWithActiveFilterWithoutError() throws Exception {
        NodeRegistry r = new NodeRegistry("self");
        r.observeSend("hermes", "h1", 1000);
        r.observeRecv("worker1", "h2", 1000);
        r.markListener("worker1", "h2", "orders", 1000);
        GraphScreen s = new GraphScreen(r);
        s.filter.topics.add("orders");                 // worker1 bright, hermes dimmed
        renderOk(s, 120, 40);
        s.onKey(new KeyStroke('g', false, false));     // gfx path must dim too (unsupported here -> stays braille)
        renderOk(s, 120, 40);
    }

    @Test
    void topicPickerSelectsAndAppliesFilter() {
        NodeRegistry r = new NodeRegistry("self");
        r.markListener("worker1", "h1", "orders", 1000);
        GraphScreen s = new GraphScreen(r);
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('t', false, false)).kind());  // open topic picker
        s.onKey(new KeyStroke(' ', false, false));                  // space toggles selection
        s.onKey(new KeyStroke(KeyType.Enter));                      // commit
        assertTrue(s.filter.topics.contains("orders"));
    }

    @Test
    void pickerEscapeDiscardsSelection() {
        NodeRegistry r = new NodeRegistry("self");
        r.markListener("worker1", "h1", "orders", 1000);
        GraphScreen s = new GraphScreen(r);
        s.onKey(new KeyStroke('t', false, false));
        s.onKey(new KeyStroke(' ', false, false));                  // tentatively select "orders"
        s.onKey(new KeyStroke(KeyType.Escape));                     // discard
        assertTrue(s.filter.topics.isEmpty());
    }

    @Test
    void nodePickerSelectsNode() {
        NodeRegistry r = new NodeRegistry("self");
        r.observeSend("hermes", "h1", 1000);
        GraphScreen s = new GraphScreen(r);
        s.onKey(new KeyStroke('n', false, false));                 // open node picker
        s.onKey(new KeyStroke(' ', false, false));
        s.onKey(new KeyStroke(KeyType.Enter));
        assertTrue(s.filter.nodes.contains("hermes"));
    }

    @Test
    void pickerRendersWithoutError() throws Exception {
        NodeRegistry r = new NodeRegistry("self");
        r.markListener("worker1", "h1", "orders", 1000);
        GraphScreen s = new GraphScreen(r);
        s.onKey(new KeyStroke('t', false, false));                 // overlay open
        renderOk(s, 120, 40);
    }

    @Test
    void pickerSearchFiltersAndTabSelectsAllMatches() {
        NodeRegistry r = new NodeRegistry("self");
        r.markListener("a", "h", "orders.created", 1000);
        r.markListener("b", "h", "orders.shipped", 1000);
        r.markListener("c", "h", "auth.login", 1000);
        GraphScreen s = new GraphScreen(r);
        s.onKey(new KeyStroke('t', false, false));                 // open topic picker (3 topics)
        for (char ch : "orders".toCharArray()) s.onKey(new KeyStroke(ch, false, false));   // type query
        s.onKey(new KeyStroke(KeyType.Tab));                       // select all matches
        s.onKey(new KeyStroke(KeyType.Enter));                     // commit
        assertTrue(s.filter.topics.contains("orders.created"));
        assertTrue(s.filter.topics.contains("orders.shipped"));
        assertFalse(s.filter.topics.contains("auth.login"), "non-matching topic stays unselected");
    }

    @Test
    void nodePickerLabelShowsIdAndHost() throws Exception {
        NodeRegistry r = new NodeRegistry("self");
        r.observeSend("hermes", "worker-03.prod.genios.de", 1000);
        GraphScreen s = new GraphScreen(r);
        s.onKey(new KeyStroke('n', false, false));                 // open node picker
        String label = s.pickerLabels.get("hermes");
        assertNotNull(label);
        assertTrue(label.contains("hermes"), "label shows the sender id");
        assertTrue(label.contains("worker-03.prod.genios.de"), "label also shows the hostname");
        renderOk(s, 120, 40);                                       // wide picker renders fine
    }

    @Test
    void flowToFreshlyDiscoveredNodeStillSpawns() throws Exception {
        // Apollo is already known; the answerer it talks to is discovered only by this very flow.
        NodeRegistry reg = new NodeRegistry("self");
        reg.observeSend("apollo", "apollo-host", 1);
        Msg msg = new Msg("orders", "m", "v", 1000);
        msg.setMsgId(new MorphiumId());
        msg.setSender("apollo");
        msg.setTimestamp(1000);
        MessageInfo m = new MessageInfo(msg, "orders");
        m.recipient = "";
        m.isExclusive = true;           // directed/exclusive request…
        m.answeredBy = "worker9";       // …answered by a node not yet in the registry
        m.answeredByHost = "wh9";
        MessageTracker tracker = new MessageTracker(10) {
            @Override public List<MessageInfo> getMessagesNewestFirst() { return List.of(m); }
        };
        GraphScreen s = new GraphScreen(reg, tracker, new FlowDeriver("self", reg));
        renderOk(s, 120, 40);
        assertTrue(s.shotCount() > 0,
                "a directed message to a node first discovered by this flow must still produce a beam");
    }

    private MessageInfo answeredExclusive(String sender, String topic, String answeredBy) {
        Msg msg = new Msg(topic, "m", "v", 1000);
        msg.setMsgId(new MorphiumId());
        msg.setSender(sender);
        msg.setTimestamp(1000);
        MessageInfo m = new MessageInfo(msg, topic);
        m.recipient = "";
        m.isExclusive = true;
        m.answeredBy = answeredBy;
        m.answeredByHost = answeredBy + "-host";
        return m;
    }

    @Test
    void nodeFilterKeepsNonMatchingFlowsOutOfTheShotPool() throws Exception {
        NodeRegistry reg = new NodeRegistry("self");
        reg.observeSend("apollo", "ah", 1);
        reg.observeSend("hermes", "hh", 1);
        reg.observeRecv("w1", "w1h", 1);
        MessageInfo apolloMsg = answeredExclusive("apollo", "orders", "w1");
        MessageInfo hermesMsg = answeredExclusive("hermes", "news", "w1");
        MessageTracker tracker = new MessageTracker(10) {
            @Override public List<MessageInfo> getMessagesNewestFirst() { return List.of(apolloMsg, hermesMsg); }
        };
        GraphScreen s = new GraphScreen(reg, tracker, new FlowDeriver("self", reg));
        s.filter.nodes.add("apollo");        // focus apollo only
        renderOk(s, 120, 40);
        assertEquals(1, s.shotCount(), "only the apollo flow may occupy a shot slot; hermes must not enter the pool");
    }

    private void renderOk(GraphScreen s, int cols, int rows) throws Exception {
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new com.googlecode.lanterna.TerminalSize(cols, rows));
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics());
        ts.stopScreen();
    }
}

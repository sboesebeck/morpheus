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

public class NodesScreenTest {

    private Msg msg(String sender, String host) {
        Msg m = new Msg("t", "m", "v", 30000);
        m.setMsgId(new MorphiumId());
        m.setSender(sender); m.setSenderHost(host);
        m.setTimestamp(System.currentTimeMillis());
        return m;
    }

    @Test
    void escPops() {
        NodesScreen s = new NodesScreen(new MessageTracker(50));
        assertEquals(Screen.Result.Kind.POP, s.onKey(new KeyStroke(KeyType.Escape)).kind());
    }

    @Test
    void vTogglesKeyingAndStays() {
        NodesScreen s = new NodesScreen(new MessageTracker(50));
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('v', false, false)).kind());
    }

    @Test
    void rendersPairRowsWithoutError() throws Exception {
        MessageTracker tracker = new MessageTracker(50);
        Msg req = msg("hermes", "app1");
        req.setTimestamp(1000);
        tracker.onInsert(req, "t");
        Msg ans = msg("worker", "h9");
        ans.setInAnswerTo(req.getMsgId());
        ans.setTimestamp(1100);
        tracker.onInsert(ans, "t");

        NodesScreen s = new NodesScreen(tracker);
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal();
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics());
        ts.stopScreen();
    }
}

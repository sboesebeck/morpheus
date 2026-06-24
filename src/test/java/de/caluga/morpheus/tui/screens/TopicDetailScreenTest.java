package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.tui.Screen;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TopicDetailScreenTest {

    private void renderOk(Screen s, int cols, int rows) throws Exception {
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new com.googlecode.lanterna.TerminalSize(cols, rows));
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics());
        ts.stopScreen();
    }

    @Test
    void exposesTopicAndListenersAndRenders() throws Exception {
        TopicDetailScreen s = new TopicDetailScreen("orders", List.of(
                new TopicDetailScreen.Listener("w1", "host-1"),
                new TopicDetailScreen.Listener("550e8400-e29b-41d4-a716-446655440000", "host-2")));
        assertEquals("orders", s.topic());
        assertEquals(2, s.listeners().size());
        renderOk(s, 100, 20);
    }

    @Test
    void escPopsAndEmptyRenders() throws Exception {
        TopicDetailScreen s = new TopicDetailScreen("orders", List.of());
        assertEquals(Screen.Result.Kind.POP, s.onKey(new KeyStroke(KeyType.Escape)).kind());
        renderOk(s, 100, 20);   // empty listener list must not throw
    }
}

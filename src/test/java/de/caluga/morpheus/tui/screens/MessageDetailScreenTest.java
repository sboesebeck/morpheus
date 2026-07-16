package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.core.MessageInfo;
import de.caluga.morpheus.tui.Screen;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MessageDetailScreenTest {

    private MessageInfo deletedMessage() {
        Msg msg = new Msg("orders", "payload text", "value", 30_000);
        msg.setMsgId(new MorphiumId());
        msg.setSender("api");
        msg.setSenderHost("host-1");
        msg.setRecipients(List.of("worker"));
        msg.setTimestamp(System.currentTimeMillis());
        MessageInfo info = new MessageInfo(msg, "orders");
        info.isDeleted = true;
        info.processedByCount = 1;
        return info;
    }

    @Test
    void snapshotKeepsPayloadAndDeletedState() {
        MessageDetailScreen screen = new MessageDetailScreen(deletedMessage());
        String lines = String.join("\n", screen.buildLines(80));

        assertTrue(lines.contains("payload text value"));
        assertTrue(lines.contains("gelöscht (lokaler Snapshot)"));
        assertTrue(lines.contains("worker"));
    }

    @Test
    void navigationKeysStayAndEscapePops() {
        MessageDetailScreen screen = new MessageDetailScreen(deletedMessage());
        assertEquals(Screen.Result.Kind.STAY, screen.onKey(new KeyStroke(KeyType.ArrowDown)).kind());
        assertEquals(Screen.Result.Kind.STAY, screen.onKey(new KeyStroke(KeyType.PageDown)).kind());
        assertEquals(Screen.Result.Kind.STAY, screen.onKey(new KeyStroke(KeyType.PageUp)).kind());
        assertEquals(Screen.Result.Kind.STAY, screen.onKey(new KeyStroke(KeyType.Home)).kind());
        assertEquals(Screen.Result.Kind.POP, screen.onKey(new KeyStroke(KeyType.Escape)).kind());
    }

    @Test
    void rendersNarrowAndAfterOverscrolling() throws Exception {
        MessageDetailScreen screen = new MessageDetailScreen(deletedMessage());
        for (int i = 0; i < 100; i++) screen.onKey(new KeyStroke(KeyType.PageDown));
        DefaultVirtualTerminal terminal = new DefaultVirtualTerminal(new TerminalSize(42, 10));
        TerminalScreen lanterna = new TerminalScreen(terminal);
        lanterna.startScreen();
        screen.draw(lanterna.newTextGraphics());
        lanterna.stopScreen();
    }
}

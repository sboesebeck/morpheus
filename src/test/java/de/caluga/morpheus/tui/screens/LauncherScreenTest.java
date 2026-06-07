package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.config.ConfigurationManager;
import de.caluga.morpheus.config.ConnectionSpec;
import de.caluga.morpheus.config.ConnectionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LauncherScreenTest {

    @TempDir Path tmp;

    private MorpheusContext ctxWithConnections() throws Exception {
        ConfigurationManager cm = new ConfigurationManager(tmp.resolve("m.properties").toString());
        ConnectionStore store = new ConnectionStore(cm);
        store.save(new ConnectionSpec("alpha", List.of("h:27017"), "d", "admin", "", "", "single", "msg", false, "127.0.0.1", 5555));
        store.save(new ConnectionSpec("beta", List.of("h:27017"), "d", "admin", "", "", "single", "msg", false, "127.0.0.1", 5555));
        return new MorpheusContext(cm);
    }

    @Test
    void listsConnectionsAndStartsOnFirst() throws Exception {
        LauncherScreen s = new LauncherScreen(ctxWithConnections());
        assertTrue(List.of("alpha", "beta").contains(s.selectedConnection()));
        assertEquals(LauncherScreen.Column.CONNECTIONS, s.activeColumn());
        assertEquals("messages", s.selectedView());
    }

    @Test
    void tabSwitchesColumn() throws Exception {
        LauncherScreen s = new LauncherScreen(ctxWithConnections());
        s.onKey(new KeyStroke(KeyType.Tab));
        assertEquals(LauncherScreen.Column.VIEWS, s.activeColumn());
    }

    @Test
    void downMovesViewSelectionWhenViewsActive() throws Exception {
        LauncherScreen s = new LauncherScreen(ctxWithConnections());
        s.onKey(new KeyStroke(KeyType.Tab));
        s.onKey(new KeyStroke(KeyType.ArrowDown));
        assertEquals("topics", s.selectedView());
    }

    @Test
    void qQuits() throws Exception {
        LauncherScreen s = new LauncherScreen(ctxWithConnections());
        assertEquals(de.caluga.morpheus.tui.Screen.Result.Kind.QUIT,
                s.onKey(new KeyStroke('q', false, false)).kind());
    }

    @Test
    void rendersConnectionNamesWithoutError() throws Exception {
        LauncherScreen s = new LauncherScreen(ctxWithConnections());
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal();
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics()); // must not throw
        ts.stopScreen();
    }
}

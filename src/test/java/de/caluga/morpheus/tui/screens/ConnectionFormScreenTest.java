package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.config.ConfigurationManager;
import de.caluga.morpheus.config.ConnectionSpec;
import de.caluga.morpheus.config.ConnectionStore;
import de.caluga.morpheus.tui.Screen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionFormScreenTest {

    @TempDir Path tmp;

    private MorpheusContext ctx() {
        return new MorpheusContext(new ConfigurationManager(tmp.resolve("m.properties").toString()));
    }

    @Test
    void editPrefillsFromExisting() {
        ConnectionSpec existing = new ConnectionSpec("prod", List.of("h:27017"), "mydb", "admin",
                "u", "pw", "single", "msg", true, "127.0.0.1", 5555);
        ConnectionFormScreen form = new ConnectionFormScreen(ctx(), existing);
        ConnectionSpec spec = form.toSpec();
        assertEquals("prod", spec.name());
        assertEquals("mydb", spec.database());
        assertTrue(spec.proxyEnabled());
    }

    @Test
    void tabMovesFocusForward() {
        ConnectionFormScreen form = new ConnectionFormScreen(ctx(), null);
        int before = form.focusIndex();
        form.onKey(new KeyStroke(KeyType.Tab));
        assertEquals(before + 1, form.focusIndex());
    }

    @Test
    void shiftTabMovesFocusBack() {
        ConnectionFormScreen form = new ConnectionFormScreen(ctx(), null);
        form.onKey(new KeyStroke(KeyType.Tab));
        form.onKey(new KeyStroke(KeyType.ReverseTab));
        assertEquals(0, form.focusIndex());
    }

    @Test
    void escapePopsWithoutSaving() {
        ConnectionFormScreen form = new ConnectionFormScreen(ctx(), null);
        assertEquals(Screen.Result.Kind.POP, form.onKey(new KeyStroke(KeyType.Escape)).kind());
    }

    @Test
    void enterSavesAndPops() throws Exception {
        MorpheusContext c = ctx();
        ConnectionSpec existing = new ConnectionSpec("toSave", List.of("h:27017"), "d", "admin",
                "", "", "single", "msg", false, "127.0.0.1", 5555);
        ConnectionFormScreen form = new ConnectionFormScreen(c, existing);
        Screen.Result r = form.onKey(new KeyStroke(KeyType.Enter));
        assertEquals(Screen.Result.Kind.POP, r.kind());
        assertTrue(new ConnectionStore(c.getConfig()).list().contains("toSave"));
    }

    @Test
    void rendersWithoutError() {
        ConnectionFormScreen form = new ConnectionFormScreen(ctx(), null);
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal();
        try {
            TerminalScreen ts = new TerminalScreen(vt);
            ts.startScreen();
            form.draw(ts.newTextGraphics());
            ts.stopScreen();
        } catch (Exception e) {
            fail(e);
        }
    }
}

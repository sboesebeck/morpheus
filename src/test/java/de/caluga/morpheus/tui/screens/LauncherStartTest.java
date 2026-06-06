package de.caluga.morpheus.tui.screens;

import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.config.ConfigurationManager;
import de.caluga.morpheus.config.ConnectionSpec;
import de.caluga.morpheus.config.ConnectionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LauncherStartTest {

    @TempDir Path tmp;

    private MorpheusContext ctx() throws Exception {
        ConfigurationManager cm = new ConfigurationManager(tmp.resolve("m.properties").toString());
        new ConnectionStore(cm).save(new ConnectionSpec("alpha", List.of("h:27017"), "d", "admin",
                "", "", "single", "msg", false, "127.0.0.1", 5555));
        return new MorpheusContext(cm);
    }

    @Test
    void viewForMapsNames() throws Exception {
        MorpheusContext c = ctx();
        LauncherScreen s = new LauncherScreen(c);
        assertTrue(s.viewFor("nodes", c) instanceof NodesScreen);
        assertNull(s.viewFor("graph (Phase 3)", c), "graph is disabled in Phase 2");
    }
}

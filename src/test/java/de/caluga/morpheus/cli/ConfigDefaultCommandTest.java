package de.caluga.morpheus.cli;

import de.caluga.morpheus.config.ConfigurationManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConfigDefaultCommandTest {

    @TempDir Path tmp;

    @Test
    void setDefaultConnectionPersistsAndWins() throws Exception {
        Path cfg = tmp.resolve("morpheus.properties");
        try (FileWriter w = new FileWriter(cfg.toFile())) {
            w.write("morphium.prod.database=p\nmorphium.dev.database=d\n");
        }
        ConfigurationManager cm = new ConfigurationManager(cfg.toString());

        ConfigCommands.setDefaultConnection(cm, "prod");

        ConfigurationManager reloaded = new ConfigurationManager(cfg.toString());
        assertEquals("prod", reloaded.getConnection());
    }
}

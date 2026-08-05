package de.caluga.morpheus.connection;

import de.caluga.morpheus.config.ConfigurationManager;
import de.caluga.morpheus.rendering.ThemeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MorphiumConnectionFactoryTest {

    @TempDir
    Path tmp;

    private ConfigurationManager configWith(String content) throws Exception {
        Path cfg = tmp.resolve("morpheus.properties");
        try (FileWriter w = new FileWriter(cfg.toFile())) {
            w.write(content);
        }
        return new ConfigurationManager(cfg.toString());
    }

    @Test
    void unknownConnectionNameGivesHelpfulError() throws Exception {
        ConfigurationManager cm = configWith("""
            morphium.prod.hostSeed=[localhost:27017]
            morphium.dev.hostSeed=[localhost:27017]
            """);
        cm.setConnectionOverride("typo");
        MorphiumConnectionFactory factory = new MorphiumConnectionFactory(cm, new ThemeManager(cm));

        IllegalStateException ex = assertThrows(IllegalStateException.class, factory::createMorphium);
        assertTrue(ex.getMessage().contains("'typo' not found"), ex.getMessage());
        assertTrue(ex.getMessage().contains("dev, prod"), ex.getMessage());
    }

    @Test
    void connectionWithoutHostsGivesHelpfulError() throws Exception {
        ConfigurationManager cm = configWith("morphium.prod.database=mydb\n");
        cm.setConnectionOverride("prod");
        MorphiumConnectionFactory factory = new MorphiumConnectionFactory(cm, new ThemeManager(cm));

        IllegalStateException ex = assertThrows(IllegalStateException.class, factory::createMorphium);
        assertTrue(ex.getMessage().contains("'prod' has no hosts configured"), ex.getMessage());
    }
}

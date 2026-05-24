package de.caluga.morpheus.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConfigurationManagerTest {

    @TempDir
    Path tmp;

    private String writeConfig(String content) throws Exception {
        Path cfg = tmp.resolve("morpheus.properties");
        try (FileWriter w = new FileWriter(cfg.toFile())) {
            w.write(content);
        }
        return cfg.toString();
    }

    @Test
    void connectionOverrideWins() throws Exception {
        String path = writeConfig("""
            morphium.prod.database=p
            morphium.dev.database=d
            morpheus.defaultConnection=dev
            """);
        ConfigurationManager cm = new ConfigurationManager(path);
        cm.setConnectionOverride("prod");
        assertEquals("prod", cm.getConnection());
    }

    @Test
    void defaultConnectionPropertyUsedWhenNoOverride() throws Exception {
        String path = writeConfig("""
            morphium.prod.database=p
            morphium.dev.database=d
            morpheus.defaultConnection=dev
            """);
        ConfigurationManager cm = new ConfigurationManager(path);
        assertEquals("dev", cm.getConnection());
    }

    @Test
    void singleConfiguredConnectionUsedImplicitly() throws Exception {
        String path = writeConfig("morphium.prod.database=p\n");
        ConfigurationManager cm = new ConfigurationManager(path);
        assertEquals("prod", cm.getConnection());
    }

    @Test
    void fallsBackToDefaultConnectionName() throws Exception {
        String path = writeConfig("""
            morphium.prod.database=p
            morphium.dev.database=d
            """);
        ConfigurationManager cm = new ConfigurationManager(path);
        assertEquals("default_connection", cm.getConnection());
    }

    @Test
    void messagingOverrideWinsOverProperty() throws Exception {
        String path = writeConfig("""
            morphium.prod.database=p
            morphium.prod.messaging.implementation=multi
            """);
        ConfigurationManager cm = new ConfigurationManager(path);
        cm.setMessagingOverride("single");
        assertEquals("single", cm.getMessagingImplementation());
    }

    @Test
    void freshConfigFileIsCreatedWithDefaults() throws Exception {
        String path = tmp.resolve("fresh.properties").toString();
        ConfigurationManager cm = new ConfigurationManager(path);
        assertEquals("default", cm.getTheme());
        assertEquals("default_connection", cm.getConnection());
    }
}

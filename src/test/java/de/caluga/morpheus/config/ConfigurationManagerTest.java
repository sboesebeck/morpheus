package de.caluga.morpheus.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void setDefaultConnectionPersistsAndClears() throws Exception {
        String path = writeConfig("""
            morphium.prod.database=p
            morphium.dev.database=d
            """);
        ConfigurationManager cm = new ConfigurationManager(path);
        assertNull(cm.getDefaultConnection());

        cm.setDefaultConnection("prod");
        assertEquals("prod", cm.getDefaultConnection());
        assertEquals("prod", new ConfigurationManager(path).getDefaultConnection(), "must persist to disk");

        cm.setDefaultConnection(null);
        assertNull(cm.getDefaultConnection());
        assertNull(new ConfigurationManager(path).getDefaultConnection(), "clearing must persist");
    }

    @Test
    void freshConfigFileIsCreatedWithDefaults() throws Exception {
        String path = tmp.resolve("fresh.properties").toString();
        ConfigurationManager cm = new ConfigurationManager(path);
        assertEquals("default", cm.getTheme());
        assertEquals("default_connection", cm.getConnection());
        assertEquals(1_000, cm.getMessageHistorySize());
    }

    @Test
    void messageHistoryDefaultsValidatesAndPersists() throws Exception {
        String path = writeConfig("morpheus.messages.history=2500\n");
        ConfigurationManager cm = new ConfigurationManager(path);
        assertEquals(2_500, cm.getMessageHistorySize());

        cm.setMessageHistorySize(5_000);
        assertEquals(5_000, new ConfigurationManager(path).getMessageHistorySize());

        cm.setMessageHistorySize(1);
        assertEquals(ConfigurationManager.MIN_MESSAGE_HISTORY_SIZE, cm.getMessageHistorySize());
        cm.setMessageHistorySize(Integer.MAX_VALUE);
        assertEquals(ConfigurationManager.MAX_MESSAGE_HISTORY_SIZE, cm.getMessageHistorySize());
    }

    @Test
    void invalidMessageHistoryFallsBackToDefault() throws Exception {
        String path = writeConfig("morpheus.messages.history=not-a-number\n");
        assertEquals(ConfigurationManager.DEFAULT_MESSAGE_HISTORY_SIZE,
                new ConfigurationManager(path).getMessageHistorySize());
    }
}

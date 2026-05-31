package de.caluga.morpheus.cli;

import de.caluga.morpheus.config.ConfigurationManager;
import de.caluga.morpheus.config.ConnectionSpec;
import de.caluga.morpheus.config.ConnectionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The CLI add path and a direct ConnectionStore.save must produce identical stored properties. */
public class ConfigConnectionParityTest {

    @TempDir Path tmp;

    @Test
    void storeSaveWritesExpectedKeys() throws Exception {
        ConfigurationManager cm = new ConfigurationManager(tmp.resolve("m.properties").toString());
        new ConnectionStore(cm).save(new ConnectionSpec("c1",
                List.of("localhost:27017"), "test", "admin",
                "", "", "single", "msg", false, "127.0.0.1", 5555));

        ConfigurationManager re = new ConfigurationManager(tmp.resolve("m.properties").toString());
        assertEquals("test", re.getProperty("morphium.c1.database"));
        assertEquals("single", re.getProperty("morphium.c1.messaging.implementation"));
        assertEquals("msg", re.getProperty("morphium.c1.messaging.queueName"));
        assertNotNull(re.getProperty("morphium.c1.messaging.senderId"));
        assertEquals("false", re.getProperty("morphium.c1.proxy.enabled"));
        assertTrue(re.getAvailableConnections().contains("c1"));
    }
}

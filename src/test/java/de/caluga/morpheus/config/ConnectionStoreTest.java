package de.caluga.morpheus.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionStoreTest {

    @TempDir Path tmp;

    private ConnectionStore store() {
        return new ConnectionStore(new ConfigurationManager(tmp.resolve("m.properties").toString()));
    }

    @Test
    void saveThenLoadRoundtrip() throws Exception {
        ConnectionStore s = store();
        ConnectionSpec spec = new ConnectionSpec("prod",
                List.of("h1:27017", "h2:27017"), "mydb", "admin",
                "user", "secret", "single", "msg",
                true, "127.0.0.1", 5555);
        s.save(spec);

        ConnectionSpec loaded = s.load("prod");
        assertEquals("mydb", loaded.database());
        assertEquals("admin", loaded.authDb());
        assertEquals("user", loaded.user());
        assertEquals("single", loaded.messagingImpl());
        assertEquals("msg", loaded.queueName());
        assertTrue(loaded.proxyEnabled());
        assertEquals("127.0.0.1", loaded.proxyHost());
        assertEquals(5555, loaded.proxyPort());
        assertTrue(loaded.hosts().contains("h1:27017"));
        assertTrue(loaded.hosts().contains("h2:27017"));
    }

    @Test
    void saveListsTheConnection() throws Exception {
        ConnectionStore s = store();
        s.save(new ConnectionSpec("dev", List.of("localhost:27017"), "d", "admin",
                "", "", "single", "msg", false, "127.0.0.1", 5555));
        assertTrue(s.list().contains("dev"));
    }

    @Test
    void emptyUserSkipsAuthKeys() throws Exception {
        ConnectionStore s = store();
        s.save(new ConnectionSpec("noauth", List.of("localhost:27017"), "d", "admin",
                "", "", "single", "msg", false, "127.0.0.1", 5555));
        ConfigurationManager cm = new ConfigurationManager(tmp.resolve("m.properties").toString());
        assertNull(cm.getProperty("morphium.noauth.mongoLogin"));
        assertNull(cm.getProperty("morphium.noauth.mongoPassword"));
    }

    @Test
    void blankPasswordKeepsExistingOnReSave() throws Exception {
        ConnectionStore s = store();
        s.save(new ConnectionSpec("p", List.of("h:27017"), "d", "admin",
                "u", "orig", "single", "msg", false, "127.0.0.1", 5555));
        s.save(new ConnectionSpec("p", List.of("h:27017"), "d", "admin",
                "u", "", "single", "msg", false, "127.0.0.1", 5555));
        ConfigurationManager cm = new ConfigurationManager(tmp.resolve("m.properties").toString());
        assertEquals("orig", cm.getProperty("morphium.p.mongoPassword"));
    }

    @Test
    void deleteRemovesConnection() throws Exception {
        ConnectionStore s = store();
        s.save(new ConnectionSpec("gone", List.of("h:27017"), "d", "admin",
                "", "", "single", "msg", false, "127.0.0.1", 5555));
        s.delete("gone");
        assertFalse(s.list().contains("gone"));
    }
}

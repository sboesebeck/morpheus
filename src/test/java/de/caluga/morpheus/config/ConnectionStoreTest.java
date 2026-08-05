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
                true, "127.0.0.1", 5555, false, false, false);
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
        assertFalse(loaded.useSSL());
        assertFalse(loaded.sslInvalidHostnameAllowed());
        assertFalse(loaded.sslTrustAllCertificates());
    }

    @Test
    void saveThenLoadRoundtripSSL() throws Exception {
        ConnectionStore s = store();
        s.save(new ConnectionSpec("secure", List.of("h:27017"), "d", "admin",
                "", "", "single", "msg", false, "127.0.0.1", 5555, true, true, false));

        ConnectionSpec loaded = s.load("secure");
        assertTrue(loaded.useSSL());
        assertTrue(loaded.sslInvalidHostnameAllowed());

        ConfigurationManager cm = new ConfigurationManager(tmp.resolve("m.properties").toString());
        assertEquals("true", cm.getProperty("morphium.secure.useSSL"));
        assertEquals("true", cm.getProperty("morphium.secure.sslInvalidHostNameAllowed"));
    }

    @Test
    void saveThenLoadRoundtripSslTrustAll() throws Exception {
        // Not a MorphiumConfig/ConnectionSettings field (an SSLContext can't come from a string
        // property), so it needs its own round-trip proof - MorphiumConfig.fromProperties()
        // never sees this key at all (see MorphiumConnectionFactory, which reads it back
        // separately).
        ConnectionStore s = store();
        s.save(new ConnectionSpec("trustall", List.of("h:27017"), "d", "admin",
                "", "", "single", "msg", false, "127.0.0.1", 5555, true, false, true));

        ConnectionSpec loaded = s.load("trustall");
        assertTrue(loaded.useSSL());
        assertFalse(loaded.sslInvalidHostnameAllowed());
        assertTrue(loaded.sslTrustAllCertificates());

        ConfigurationManager cm = new ConfigurationManager(tmp.resolve("m.properties").toString());
        assertEquals("true", cm.getProperty("morphium.trustall.sslTrustAllCertificates"));
    }

    @Test
    void reSaveWithSslTrustAllDisabledOverwritesPreviousTrueValue() throws Exception {
        ConnectionStore s = store();
        s.save(new ConnectionSpec("toggletrust", List.of("h:27017"), "d", "admin",
                "", "", "single", "msg", false, "127.0.0.1", 5555, true, false, true));
        s.save(new ConnectionSpec("toggletrust", List.of("h:27017"), "d", "admin",
                "", "", "single", "msg", false, "127.0.0.1", 5555, true, false, false));

        assertFalse(s.load("toggletrust").sslTrustAllCertificates());
    }

    /**
     * The property names {@link ConnectionStore#save} writes for useSSL/sslInvalidHostNameAllowed
     * must be exactly the ones {@link MorphiumConfig#fromProperties} reads (it maps by reflection
     * on the {@code ConnectionSettings} field name) - a typo on either side would silently
     * downgrade an SSL connection to plaintext with no error anywhere. sslTrustAllCertificates is
     * excluded here on purpose: it is NOT a MorphiumConfig field (see above), so it is read back
     * by Morpheus's own code, not fromProperties() - {@link #saveThenLoadRoundtripSslTrustAll}
     * covers that path instead.
     */
    @Test
    void savedSslPropertiesAreWiredToTheActualDriverConfig() throws Exception {
        ConnectionStore s = store();
        s.save(new ConnectionSpec("wired", List.of("h:27017"), "d", "admin",
                "", "", "single", "msg", false, "127.0.0.1", 5555, true, true, false));

        ConfigurationManager cm = new ConfigurationManager(tmp.resolve("m.properties").toString());
        var driverCfg = de.caluga.morphium.MorphiumConfig.fromProperties("morphium.wired", cm.getProperties());
        assertTrue(driverCfg.isUseSSL(), "useSSL must reach MorphiumConfig, not just ConnectionSpec/properties");
        assertTrue(driverCfg.isSslInvalidHostNameAllowed(),
                "sslInvalidHostNameAllowed must reach MorphiumConfig, not just ConnectionSpec/properties");
    }

    @Test
    void reSaveWithSslDisabledOverwritesPreviousTrueValue() throws Exception {
        // Regression test: MorphiumConfig's Settings.asProperties() only serializes fields that
        // differ from their default, so a naive re-save could silently leave a stale "true" behind.
        ConnectionStore s = store();
        s.save(new ConnectionSpec("toggle", List.of("h:27017"), "d", "admin",
                "", "", "single", "msg", false, "127.0.0.1", 5555, true, true, false));
        s.save(new ConnectionSpec("toggle", List.of("h:27017"), "d", "admin",
                "", "", "single", "msg", false, "127.0.0.1", 5555, false, false, false));

        ConnectionSpec loaded = s.load("toggle");
        assertFalse(loaded.useSSL());
        assertFalse(loaded.sslInvalidHostnameAllowed());
    }

    @Test
    void saveListsTheConnection() throws Exception {
        ConnectionStore s = store();
        s.save(new ConnectionSpec("dev", List.of("localhost:27017"), "d", "admin",
                "", "", "single", "msg", false, "127.0.0.1", 5555, false, false, false));
        assertTrue(s.list().contains("dev"));
    }

    @Test
    void emptyUserSkipsAuthKeys() throws Exception {
        ConnectionStore s = store();
        s.save(new ConnectionSpec("noauth", List.of("localhost:27017"), "d", "admin",
                "", "", "single", "msg", false, "127.0.0.1", 5555, false, false, false));
        ConfigurationManager cm = new ConfigurationManager(tmp.resolve("m.properties").toString());
        assertNull(cm.getProperty("morphium.noauth.mongoLogin"));
        assertNull(cm.getProperty("morphium.noauth.mongoPassword"));
    }

    @Test
    void blankPasswordKeepsExistingOnReSave() throws Exception {
        ConnectionStore s = store();
        s.save(new ConnectionSpec("p", List.of("h:27017"), "d", "admin",
                "u", "orig", "single", "msg", false, "127.0.0.1", 5555, false, false, false));
        s.save(new ConnectionSpec("p", List.of("h:27017"), "d", "admin",
                "u", "", "single", "msg", false, "127.0.0.1", 5555, false, false, false));
        ConfigurationManager cm = new ConfigurationManager(tmp.resolve("m.properties").toString());
        assertEquals("orig", cm.getProperty("morphium.p.mongoPassword"));
    }

    @Test
    void deleteRemovesConnection() throws Exception {
        ConnectionStore s = store();
        s.save(new ConnectionSpec("gone", List.of("h:27017"), "d", "admin",
                "", "", "single", "msg", false, "127.0.0.1", 5555, false, false, false));
        s.delete("gone");
        assertFalse(s.list().contains("gone"));
    }
}

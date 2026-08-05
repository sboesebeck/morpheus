package de.caluga.morpheus.cli;

import de.caluga.morpheus.config.ConfigurationManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code config ssl enable|disable|config} - previously entirely untested (flagged in review).
 * Exercises {@link ConfigCommands.Ssl#call()} directly against a package-private field chain
 * (same package as {@link ConfigCommands}, matching the test-seam pattern already used by
 * {@code ConnectionFormScreenTest}) rather than full CLI argument parsing, since
 * {@link RootCommand#context()} otherwise always resolves the real default config path.
 */
public class SslCommandTest {

    @TempDir Path tmp;

    private ConfigCommands.Ssl sslCommand(ConfigurationManager cm, String action, String name) {
        RootCommand root = new RootCommand();
        root.setContextForTest(new MorpheusContext(cm));
        ConfigCommands cc = new ConfigCommands();
        cc.parent = root;
        ConfigCommands.Ssl ssl = new ConfigCommands.Ssl();
        ssl.parent = cc;
        ssl.action = action;
        ssl.name = name;
        return ssl;
    }

    private ConfigurationManager cm() {
        return new ConfigurationManager(tmp.resolve("m.properties").toString());
    }

    @Test
    void enableSetsUseSSL() throws Exception {
        ConfigurationManager cm = cm();
        cm.setProperty("morphium.prod.database", "d"); // connection must exist for property writes to be found later
        cm.save();
        assertEquals(0, sslCommand(cm, "enable", "prod").call());

        ConfigurationManager reread = cm();
        assertEquals("true", reread.getProperty("morphium.prod.useSSL"));
    }

    @Test
    void disableResetsUseSslAndBothWeakeningFlagsEvenIfPreviouslyTrue() throws Exception {
        // Regression test (review finding): 'disable' used to only clear useSSL, leaving
        // sslInvalidHostNameAllowed/sslTrustAllCertificates "true" on disk - so a later
        // 'enable' would silently come back with hostname verification (or ALL certificate
        // validation) still off.
        ConfigurationManager cm = cm();
        cm.setProperty("morphium.prod.useSSL", "true");
        cm.setProperty("morphium.prod.sslInvalidHostNameAllowed", "true");
        cm.setProperty("morphium.prod.sslTrustAllCertificates", "true");
        cm.save();

        assertEquals(0, sslCommand(cm, "disable", "prod").call());

        ConfigurationManager reread = cm();
        assertEquals("false", reread.getProperty("morphium.prod.useSSL"));
        assertEquals("false", reread.getProperty("morphium.prod.sslInvalidHostNameAllowed"));
        assertEquals("false", reread.getProperty("morphium.prod.sslTrustAllCertificates"));
    }

    @Test
    void configActionWritesAllThreeFlagsFromInteractivePrompt() throws Exception {
        ConfigurationManager cm = cm();
        cm.save();

        java.io.InputStream originalIn = System.in;
        System.setIn(new ByteArrayInputStream("yes\nyes\nyes\n".getBytes()));
        try {
            assertEquals(0, sslCommand(cm, "config", "prod").call());
        } finally {
            System.setIn(originalIn);
        }

        ConfigurationManager reread = cm();
        assertEquals("true", reread.getProperty("morphium.prod.useSSL"));
        assertEquals("true", reread.getProperty("morphium.prod.sslInvalidHostNameAllowed"));
        assertEquals("true", reread.getProperty("morphium.prod.sslTrustAllCertificates"));
    }

    @Test
    void configActionDefaultsToNoForHostnameAndTrustAllOnBareEnter() throws Exception {
        ConfigurationManager cm = cm();
        cm.save();

        java.io.InputStream originalIn = System.in;
        System.setIn(new ByteArrayInputStream("\n\n\n".getBytes())); // accept every default
        try {
            assertEquals(0, sslCommand(cm, "config", "prod").call());
        } finally {
            System.setIn(originalIn);
        }

        ConfigurationManager reread = cm();
        assertEquals("true", reread.getProperty("morphium.prod.useSSL")); // default: yes
        assertEquals("false", reread.getProperty("morphium.prod.sslInvalidHostNameAllowed")); // default: no
        assertEquals("false", reread.getProperty("morphium.prod.sslTrustAllCertificates")); // default: no
    }

    @Test
    void configActionAnsweringNoToEnableForcesBothWeakeningFlagsFalseWithoutEvenAsking() throws Exception {
        // Regression test (review finding): previously 'config' asked all three questions
        // unconditionally, so "Enable? no" / "Trust all? yes" wrote useSSL=false with
        // sslTrustAllCertificates=true still on disk - a second, silent way (besides the old
        // 'disable' bug) for a later 'ssl enable' to come back with validation off. Only one
        // line of stdin is provided here - if the implementation still asked the other two
        // questions, this would hang/read past EOF and the test would fail rather than pass.
        ConfigurationManager cm = cm();
        cm.setProperty("morphium.prod.sslInvalidHostNameAllowed", "true");
        cm.setProperty("morphium.prod.sslTrustAllCertificates", "true");
        cm.save();

        java.io.InputStream originalIn = System.in;
        System.setIn(new ByteArrayInputStream("no\n".getBytes()));
        try {
            assertEquals(0, sslCommand(cm, "config", "prod").call());
        } finally {
            System.setIn(originalIn);
        }

        ConfigurationManager reread = cm();
        assertEquals("false", reread.getProperty("morphium.prod.useSSL"));
        assertEquals("false", reread.getProperty("morphium.prod.sslInvalidHostNameAllowed"));
        assertEquals("false", reread.getProperty("morphium.prod.sslTrustAllCertificates"));
    }

    @Test
    void unknownActionReturnsErrorExitCode() throws Exception {
        ConfigurationManager cm = cm();
        cm.save();
        assertEquals(2, sslCommand(cm, "bogus", "prod").call());
    }
}

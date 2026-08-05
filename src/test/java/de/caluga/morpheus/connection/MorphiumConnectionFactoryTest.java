package de.caluga.morpheus.connection;

import de.caluga.morpheus.config.ConfigurationManager;
import de.caluga.morpheus.rendering.ThemeManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileWriter;
import java.net.ServerSocket;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    /** A closed local port: refuses immediately, like an unreachable host. */
    private int deadPort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** Open only by {@link #sslDeadPort()}; closed in {@link #closeSslDeadPort()} so the
     * server thread's {@code accept()} loop actually terminates instead of leaking for the rest
     * of the surefire JVM. */
    private ServerSocket sslServerSocket;

    /** A listening plain-TCP port that accepts every connection and immediately writes bytes
     * that are not a valid TLS record. A real SSL client handshaking against this triggers a
     * genuine {@link javax.net.ssl.SSLException} (not just "connection refused") - the actual
     * failure mode this class exists to make visible. Must serve EVERY connection, not just the
     * first: {@code createMorphium()} retries for up to ~10s, and {@code lastConnectFailure} only
     * holds the most recent attempt's error - if a later retry hit a since-closed port, its bland
     * "connection refused" would silently overwrite the real SSL error from the first attempt. */
    private int sslDeadPort() throws Exception {
        ServerSocket ss = new ServerSocket(0);
        sslServerSocket = ss;
        int port = ss.getLocalPort();
        Thread t = new Thread(() -> {
            while (!ss.isClosed()) {
                try {
                    java.net.Socket client = ss.accept();
                    client.getOutputStream().write(new byte[]{0x00, 0x00, 0x00, 0x00, 0x00});
                    client.getOutputStream().flush();
                    Thread.sleep(50);
                    client.close();
                } catch (Exception ignored) {
                    // server socket closed (test teardown) or client disconnected mid-handshake
                }
            }
        }, "ssl-dead-port");
        t.setDaemon(true);
        t.start();
        return port;
    }

    @AfterEach
    void closeSslDeadPort() throws Exception {
        if (sslServerSocket != null) {
            sslServerSocket.close();
            sslServerSocket = null;
        }
    }

    @Test
    void interruptingTheRetryWaitPropagatesInterruptedExceptionAsIsNotWrappedAsIllegalState() throws Exception {
        // Regression test: createMorphium()'s new try/catch (added to close the Morphium instance
        // on any failure - see the leak this whole block exists to fix) must not swallow
        // Thread.sleep()'s InterruptedException into an IllegalStateException. ConnectionTester
        // specifically distinguishes InterruptedException (its own watchdog interrupting the
        // worker after a timeout - it must NOT overwrite the "✗ Timeout" it already set) from
        // every other exception (which DOES get shown, overwriting the status). Wrapping would
        // silently break that distinction.
        int port = deadPort();
        ConfigurationManager cm = configWith("morphium.prod.hostSeed=[localhost:" + port + "]\n");
        cm.setConnectionOverride("prod");
        MorphiumConnectionFactory factory = new MorphiumConnectionFactory(cm, new ThemeManager(cm));

        java.util.concurrent.atomic.AtomicReference<Throwable> caught = new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                factory.createMorphium();
            } catch (Throwable t) {
                caught.set(t);
            }
        });
        worker.start();
        // Give Morphium's own construction (annotation/classpath scanning etc.) time to finish
        // first - interrupting too early lands inside that instead of the retry-wait loop this
        // regression is actually about, and surfaces an unrelated exception type from there.
        Thread.sleep(3000);
        worker.interrupt();
        worker.join(2000);

        assertFalse(worker.isAlive(), "worker did not terminate after being interrupted");
        assertTrue(caught.get() instanceof InterruptedException,
                "expected InterruptedException to propagate as-is, got: " + caught.get());
    }

    @Test
    void unreachableHostFailureIsNotSwallowed() throws Exception {
        int port = deadPort();
        ConfigurationManager cm = configWith("morphium.prod.hostSeed=[localhost:" + port + "]\n");
        cm.setConnectionOverride("prod");
        MorphiumConnectionFactory factory = new MorphiumConnectionFactory(cm, new ThemeManager(cm));

        IllegalStateException ex = assertThrows(IllegalStateException.class, factory::createMorphium);

        // The generic "after N attempts" headline stays (still useful context), but it must no
        // longer be the whole story: a real cause has to be attached. The cause's own detail is
        // deliberately NOT folded into this exception's message too - the UI layers that render
        // this (ConnectingScreen, ConnectionTester) append Failures.causeDetail() themselves;
        // doing it here as well would just duplicate the text on screen.
        assertNotNull(ex.getCause(), "the real connect failure must be attached as the cause, not swallowed");
    }

    @Test
    void sslConnectFailureIsNotSwallowed() throws Exception {
        // Named deliberately more modestly than "...mentionsTheRealTlsError": which of two
        // legitimate failure shapes surfaces here is racy (see comment below), so this asserts
        // the contract that actually always holds - a real, non-generic cause is attached, never
        // a bare "failed to connect" with nothing behind it. See
        // trustAllAlsoDisablesHostnameVerificationNotJustCertificateChainValidation and
        // trustAllIsOffByDefaultEvenWithSslEnabled for deterministic coverage of the SSL config
        // wiring itself.
        int port = sslDeadPort();
        ConfigurationManager cm = configWith("""
            morphium.prod.hostSeed=[localhost:%d]
            morphium.prod.useSSL=true
            """.formatted(port));
        cm.setConnectionOverride("prod");
        MorphiumConnectionFactory factory = new MorphiumConnectionFactory(cm, new ThemeManager(cm));

        IllegalStateException ex = assertThrows(IllegalStateException.class, factory::createMorphium);

        assertNotNull(ex.getCause(), "must not swallow the connect failure just because SSL is on");
        Throwable root = ex.getCause();
        StringBuilder chain = new StringBuilder();
        for (Throwable c = root; c != null && c != c.getCause(); c = c.getCause()) {
            chain.append(c.getClass().getName()).append(": ").append(c.getMessage()).append('\n');
        }
        // Two legitimate failure shapes race here:
        // (a) isConnected() never turns true -> pd.getLastConnectFailure() carries the SSL
        //     failure directly (the common case, and the one that reliably surfaces "SSLException").
        // (b) isConnected() flips true just long enough for the pool to register the connection,
        //     the SSL-broken connection then gets evicted by housekeeping, and the later
        //     getReadConnection() probe throws PooledDriver's own "Host ... was removed while
        //     waiting for a connection" - a real, non-swallowed failure, just without the
        //     original SSL exception re-attached as ITS cause (a separate, pre-existing gap in
        //     PooledDriver's pool bookkeeping, out of scope here - see
        //     unreachableHostFailureIsNotSwallowed for the same race on the plain-TCP case).
        // A cause-less/genuinely-swallowed failure would produce neither string below, so this
        // still catches an actual regression - it just doesn't pin down which of the two
        // legitimate shapes won the race.
        assertTrue(chain.toString().contains("SSL") || chain.toString().contains("removed while waiting"),
                "must carry real diagnostic detail, not a generic swallowed failure: " + chain);
    }

    @Test
    void trustAllAlsoDisablesHostnameVerificationNotJustCertificateChainValidation() throws Exception {
        // Regression test (review finding): a trust-all X509TrustManager alone only skips
        // certificate-chain validation - it does NOT skip hostname/endpoint verification. Without
        // also setting sslInvalidHostNameAllowed, the flag's headline promise ("ALL certificates
        // trusted") is false, and the typical dev case (self-signed cert, CN/SAN mismatch) still
        // fails even after the user explicitly asked to trust everything.
        ConfigurationManager cm = configWith("""
            morphium.prod.hostSeed=[localhost:27017]
            morphium.prod.useSSL=true
            morphium.prod.sslTrustAllCertificates=true
            """);
        cm.setConnectionOverride("prod");
        MorphiumConnectionFactory factory = new MorphiumConnectionFactory(cm, new ThemeManager(cm));

        de.caluga.morphium.MorphiumConfig cfg = factory.buildConfig();

        assertTrue(cfg.isSslInvalidHostNameAllowed(),
                "sslTrustAllCertificates must imply sslInvalidHostNameAllowed, otherwise the "
                        + "'ALL certificates trusted' promise is false");
    }

    @Test
    void trustAllIsOffByDefaultEvenWithSslEnabled() throws Exception {
        ConfigurationManager cm = configWith("""
            morphium.prod.hostSeed=[localhost:27017]
            morphium.prod.useSSL=true
            """);
        cm.setConnectionOverride("prod");
        MorphiumConnectionFactory factory = new MorphiumConnectionFactory(cm, new ThemeManager(cm));

        de.caluga.morphium.MorphiumConfig cfg = factory.buildConfig();

        assertFalse(cfg.isSslInvalidHostNameAllowed(),
                "must not weaken hostname verification unless sslTrustAllCertificates is explicitly set");
    }
}

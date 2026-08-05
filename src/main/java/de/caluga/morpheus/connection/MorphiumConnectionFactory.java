package de.caluga.morpheus.connection;

import de.caluga.morpheus.config.ConfigurationManager;
import de.caluga.morpheus.rendering.ThemeManager;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import de.caluga.morphium.driver.ReadPreference;
import de.caluga.morphium.driver.wire.PooledDriver;
import de.caluga.morphium.driver.wire.SslHelper;
import de.caluga.morphium.messaging.DualChannelMessaging;
import de.caluga.morphium.messaging.MorphiumMessaging;
import de.caluga.morphium.messaging.MultiCollectionMessaging;
import de.caluga.morphium.messaging.SingleCollectionMessaging;

/**
 * Factory for creating and configuring Morphium connections and messaging
 */
public class MorphiumConnectionFactory {
    private final ConfigurationManager config;
    private final ThemeManager theme;

    public MorphiumConnectionFactory(ConfigurationManager config, ThemeManager theme) {
        this.config = config;
        this.theme = theme;
    }

    public Morphium createMorphium() throws Exception {
        MorphiumConfig cfg = buildConfig();
        boolean verbose = config.isVerbose();

        // Configure messaging
        ConfigurationManager.MessagingConfig msgConfig = config.getMessagingConfig();
        var msgSettings = cfg.messagingSettings();
        msgSettings.setSenderId("Morpheus@" + System.currentTimeMillis());
        msgSettings.setProcessMultiple(msgConfig.processMultiple);
        msgSettings.setMessagingMultithreadded(msgConfig.multithreadded);
        msgSettings.setMessagingPollPause(msgConfig.pause);
        msgSettings.setMessagingWindowSize(msgConfig.windowSize);

        // Only set queue name if explicitly configured (null means use Morphium default)
        // if (msgConfig.queueName != null) {


        msgSettings.setMessageQueueName(msgConfig.queueName);
        if (msgConfig.queueName != null && msgConfig.queueName.equals("msg")) {
            msgSettings.setMessageQueueName(null);
        }
        // }

        msgSettings.setSenderId(msgConfig.senderId);

        // Set messaging implementation
        switch (msgConfig.implementation.toLowerCase()) {
            case "multi":
                msgSettings.setMessagingClass(MultiCollectionMessaging.class);
                if (verbose) {
                    theme.print("  Messaging: [c3]MultiCollectionMessaging[r]");
                }
                break;
            case "dual":
                msgSettings.setMessagingClass(DualChannelMessaging.class);
                if (verbose) {
                    theme.print("  Messaging: [c3]DualChannelMessaging[r] (beta)");
                }
                break;
            default:
                msgSettings.setMessagingClass(SingleCollectionMessaging.class);
                if (verbose) {
                    theme.print("  Messaging: [c3]SingleCollectionMessaging[r]");
                }
                break;
        }

        // Create Morphium instance
        Morphium morphium = new Morphium(cfg);

        // Everything below either returns morphium ready-to-use or throws - and every throwing
        // path must close the instance it just created first. Without this, a failed connect
        // attempt leaks a Morphium (and its housekeeping/heartbeat threads, which keep retrying
        // in the background) every single time - e.g. every time the user presses [t] on a
        // connection that's actually down.
        try {
            // Wait for connection
            PooledDriver pd = (PooledDriver) morphium.getDriver();
            int attempts = 0;
            while (!morphium.getDriver().isConnected() && attempts < 10) {
                if (verbose) {
                    theme.print("[warning]Waiting for connection... (" + (attempts + 1) + "/10)[r]");
                }
                Thread.sleep(1000);
                attempts++;
            }

            if (!morphium.getDriver().isConnected()) {
                // Message stays plain here - callers (ConnectingScreen, ConnectionTester) are the
                // ones that render this to a user and already append Failures.causeDetail() to
                // whatever they show; folding it in here too would just duplicate it.
                throw new IllegalStateException(
                        "Failed to connect to MongoDB after " + attempts + " attempts",
                        pd.getLastConnectFailure());
            }

            if (verbose) {
                theme.print("[good]Connected to MongoDB[r]");
            }

            // Test connection - isConnected() can transiently report true even for a seed that
            // never really came up (e.g. the pool briefly registers it before housekeeping
            // removes it again), so this explicit "borrow a connection and hand it back" probe
            // is the only way to catch that.
            var con = pd.getReadConnection(null);
            pd.releaseConnection(con);
        } catch (Throwable t) {
            try {
                morphium.close();
            } catch (Throwable closeFailure) {
                // Don't let a failure while closing hide the real connect failure below - that
                // would be a worse regression than the leak this whole try/catch exists to fix.
                t.addSuppressed(closeFailure);
            }
            if (t instanceof InterruptedException ie) {
                // Restore the flag and rethrow as-is (not wrapped below): ConnectionTester relies
                // on seeing a real InterruptedException - from its own watchdog interrupting this
                // thread - to know NOT to overwrite the "✗ Timeout" it already set. Wrapping it
                // here would silently replace that timeout message with a generic connect-failure
                // one, since InterruptedException is itself a checked Exception.
                Thread.currentThread().interrupt();
                throw ie;
            }
            if (t instanceof IllegalStateException ise) {
                throw ise;
            }
            if (t instanceof Exception ex) {
                // Includes RuntimeExceptions like MorphiumDriverException (e.g. from the
                // getReadConnection() probe below) - deliberately still wrapped, not passed
                // through: every caller (ConnectingScreen, ConnectionTester, LauncherScreen)
                // relies on this method's failures always taking this one consistent shape.
                throw new IllegalStateException("Failed to connect to MongoDB", ex);
            }
            // Error (e.g. OutOfMemoryError) - let it propagate unwrapped, same as before this
            // try/catch existed.
            throw (Error) t;
        }

        return morphium;
    }

    /** Loads the connection's {@link MorphiumConfig} and applies pool/SSL settings, but does not
     * connect. Package-private test seam so trust-all/hostname-verification wiring (see
     * ConnectionStoreTest and the trust-all handling below) can be asserted directly against the
     * built config, instead of only indirectly through a live connect attempt. */
    MorphiumConfig buildConfig() throws Exception {
        String connection = config.getConnection();
        boolean verbose = config.isVerbose();

        if (verbose) {
            theme.print("  Loading Morphium configuration: [c2]" + connection + "[r]");
        }

        // Apply SOCKS proxy settings before connecting
        ConfigurationManager.ProxyConfig proxyConfig = config.getProxyConfig();
        proxyConfig.apply();

        if (verbose && proxyConfig.enabled) {
            theme.print("  SOCKS Proxy: [c3]" + proxyConfig.host + ":" + proxyConfig.port + "[r]");
        }

        // Load Morphium configuration
        MorphiumConfig cfg = MorphiumConfig.fromProperties("morphium." + connection, config.getProperties());

        // Not a MorphiumConfig/ConnectionSettings field (an SSLContext can't be built from a
        // string property), so it's read back explicitly here rather than via fromProperties() -
        // see ConnectionStore#save for where it's written.
        boolean sslTrustAll = "true".equals(
                config.getProperty("morphium." + connection + ".sslTrustAllCertificates", "false"));
        if (cfg.isUseSSL() && sslTrustAll) {
            try {
                cfg.setSslContext(SslHelper.createTrustAllSslContext());
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to build trust-all SSLContext for connection '" + connection + "'", e);
            }
            // "Trust ALL certificates" means exactly that - a trust-all X509TrustManager alone
            // only skips certificate-chain validation, it does NOT skip hostname verification
            // (SingleMongoConnection still sets HTTPS endpoint identification unless this is
            // also set). Without this, the typical self-signed-cert dev case (CN/SAN mismatch)
            // would still fail after the user explicitly asked to trust everything - defeating
            // the whole point of the flag.
            cfg.setSslInvalidHostNameAllowed(true);
        }

        if (cfg.isUseSSL()) {
            // Loud and unconditional (not gated on --verbose): a user who turned on trust-all
            // must see it every time, not only when they happen to pass --verbose.
            if (sslTrustAll) {
                theme.print("[error]  SSL: enabled - ⚠ ALL CERTIFICATES TRUSTED, validation is OFF[r]");
            } else if (verbose) {
                theme.print("  SSL: [c3]enabled[r]"
                        + (cfg.isSslInvalidHostNameAllowed() ? " (invalid hostname allowed)" : ""));
            }
        }

        if (cfg.getHostSeed() == null || cfg.getHostSeed().isEmpty()) {
            if (!config.getAvailableConnections().contains(connection)) {
                throw new IllegalStateException("Connection '" + connection + "' not found in "
                        + config.getConfigFilePath() + ". Available connections: "
                        + String.join(", ", config.getAvailableConnections()));
            }
            throw new IllegalStateException("Connection '" + connection
                    + "' has no hosts configured (morphium." + connection + ".hostSeed) in "
                    + config.getConfigFilePath());
        }

        // Set connection pool settings
        cfg.setMinConnections(2);
        cfg.setMaxConnections(8);
        cfg.setHousekeepingTimeout(1000);
        cfg.setDefaultReadPreference(ReadPreference.secondaryPreferred());
        cfg.setMaxWaitTime(10000);

        return cfg;
    }

    public MorphiumMessaging createMessaging(Morphium morphium) throws Exception {
        boolean verbose = config.isVerbose();

        if (verbose) {
            theme.print("  Starting messaging system...");
        }

        MorphiumMessaging messaging = morphium.createMessaging();
        messaging.start();

        if (verbose) {
            theme.print("[good]Messaging system started[r]");
            theme.print("=> Using queuename [good]" + messaging.getQueueName() + "[r] and Collection [good]" + messaging.getCollectionName() + "[r]");
        }

        return messaging;
    }
}

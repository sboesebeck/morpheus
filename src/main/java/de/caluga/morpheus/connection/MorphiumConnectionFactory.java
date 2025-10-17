package de.caluga.morpheus.connection;

import de.caluga.morpheus.config.ConfigurationManager;
import de.caluga.morpheus.rendering.ThemeManager;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import de.caluga.morphium.driver.ReadPreference;
import de.caluga.morphium.driver.wire.PooledDriver;
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

        if (cfg.getHostSeed() == null || cfg.getHostSeed().isEmpty()) {
            throw new IllegalStateException("Connection not configured properly - no hosts set");
        }

        // Set connection pool settings
        cfg.setMinConnections(2);
        cfg.setMaxConnections(8);
        cfg.setHousekeepingTimeout(1000);
        cfg.setDefaultReadPreference(ReadPreference.secondaryPreferred());
        cfg.setMaxWaitTime(10000);

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
        if (msgConfig.implementation.equalsIgnoreCase("multi")) {
            msgSettings.setMessagingClass(MultiCollectionMessaging.class);
            if (verbose) {
                theme.print("  Messaging: [c3]MultiCollectionMessaging[r]");
            }
        } else {
            msgSettings.setMessagingClass(SingleCollectionMessaging.class);
            if (verbose) {
                theme.print("  Messaging: [c3]SingleCollectionMessaging[r]");
            }
        }

        // Create Morphium instance
        Morphium morphium = new Morphium(cfg);

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
            throw new IllegalStateException("Failed to connect to MongoDB after " + attempts + " attempts");
        }

        if (verbose) {
            theme.print("[good]Connected to MongoDB[r]");
        }

        // Test connection
        var con = pd.getReadConnection(null);
        pd.releaseConnection(con);

        return morphium;
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

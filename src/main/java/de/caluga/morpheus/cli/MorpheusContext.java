package de.caluga.morpheus.cli;

import de.caluga.morpheus.Version;
import de.caluga.morpheus.config.ConfigurationManager;
import de.caluga.morpheus.connection.MorphiumConnectionFactory;
import de.caluga.morpheus.rendering.ThemeManager;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.messaging.MorphiumMessaging;

/**
 * Shared services for CLI commands: configuration, theme output, and a
 * lazily opened Morphium connection. One instance per process invocation.
 */
public class MorpheusContext implements AutoCloseable {
    private final ConfigurationManager config;
    private final ThemeManager theme;
    private Morphium morphium;
    private MorphiumMessaging messaging;

    public MorpheusContext(ConfigurationManager config) {
        this.config = config;
        this.theme = new ThemeManager(config);
    }

    public ConfigurationManager getConfig() { return config; }
    public ThemeManager getTheme() { return theme; }

    public void pr(String s) { theme.print(s); }
    public void pr(String s, int gradientNr) { theme.print(s, gradientNr); }
    public String getColumn(String s, int len) { return theme.getColumn(s, len); }

    public void printBanner() {
        theme.print("[header1]Morpheus[r] v" + Version.VERSION
            + "  [c3]connection:[r] " + config.getConnection());
    }

    /** Opens Morphium + messaging on first use. */
    public synchronized void connect() throws Exception {
        if (morphium != null) return;
        if (config.isVerbose()) {
            theme.print("[c3]Connecting to MongoDB...[r]");
        }
        MorphiumConnectionFactory factory = new MorphiumConnectionFactory(config, theme);
        morphium = factory.createMorphium();
        messaging = factory.createMessaging(morphium);
        if (config.isVerbose()) {
            theme.print("[good]Connected[r]");
        }
    }

    public Morphium getMorphium() { return morphium; }
    public MorphiumMessaging getMessaging() { return messaging; }

    @Override
    public synchronized void close() {
        try {
            if (messaging != null) messaging.terminate();
        } catch (Exception e) {
            // ignore cleanup errors
        }
        try {
            if (morphium != null) morphium.close();
        } catch (Exception e) {
            // ignore cleanup errors
        }
        morphium = null;
        messaging = null;
    }
}

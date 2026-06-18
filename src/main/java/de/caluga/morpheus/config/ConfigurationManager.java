package de.caluga.morpheus.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Centralized configuration management for Morpheus
 */
public class ConfigurationManager {
    private final Properties properties;
    private final String configFilePath;

    private String themeOverride;
    private String connectionOverride;
    private String messagingOverride;
    private boolean verbose;

    public ConfigurationManager() {
        this(System.getProperty("user.home") + "/.config/morpheus.properties");
    }

    public ConfigurationManager(String configFilePath) {
        this.properties = new Properties();
        this.configFilePath = configFilePath;
        loadConfiguration();
    }

    public void setThemeOverride(String theme) { this.themeOverride = theme; }
    public void setConnectionOverride(String connection) { this.connectionOverride = connection; }
    public void setMessagingOverride(String messaging) { this.messagingOverride = messaging; }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    /** The connection name marked as default (morpheus.defaultConnection), or null if none is set. */
    public String getDefaultConnection() {
        String v = properties.getProperty("morpheus.defaultConnection");
        return (v == null || v.isBlank()) ? null : v;
    }

    /** Marks the given connection as default (or clears it when null/blank) and persists. */
    public void setDefaultConnection(String name) throws Exception {
        if (name == null || name.isBlank()) {
            properties.remove("morpheus.defaultConnection");
        } else {
            properties.setProperty("morpheus.defaultConnection", name);
        }
        save();
    }

    private void loadConfiguration() {
        File configFile = new File(configFilePath);

        if (!configFile.exists()) {
            createDefaultConfiguration();
        } else {
            try {
                properties.load(new FileReader(configFile));
            } catch (IOException e) {
                throw new RuntimeException("Failed to load configuration from: " + configFilePath, e);
            }
        }
    }

    private void createDefaultConfiguration() {
        // Default theme settings
        properties.setProperty("theme.default.c1", "[rd]");
        properties.setProperty("theme.default.c2", "[bg1][bld][fg230]");
        properties.setProperty("theme.default.c3", "[fg33]");
        properties.setProperty("theme.default.header1", "[bld]");
        properties.setProperty("theme.default.header2", "[ital]");
        properties.setProperty("theme.default.error", "[rd]");
        properties.setProperty("theme.default.warning", "[y]");
        properties.setProperty("theme.default.good", "[gr]");
        properties.setProperty("theme.default.gradient1", "grey");
        properties.setProperty("theme.default.gradient2", "green");
        properties.setProperty("theme.default.gradient3", "yellow");

        try {
            StringBuilder doc = new StringBuilder();
            doc.append("Default configuration for morpheus\n");
            doc.append("Define morphium connection / settings with prefixes morphium.CONNECTIONNAME\n");
            doc.append("You can then refer to it via commandline\n");
            doc.append("Theme definition:\n");
            doc.append("Define the color-settings mentioned below, settings refer to keys\n");
            properties.store(new FileWriter(configFilePath), doc.toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create default configuration at: " + configFilePath, e);
        }
    }

    public String getTheme() {
        return themeOverride != null ? themeOverride : "default";
    }

    public Set<String> getAvailableThemes() {
        Set<String> themes = new HashSet<>();
        for (Object key : properties.keySet()) {
            if (key.toString().startsWith("theme.")) {
                String themeName = key.toString().split("\\.")[1];
                themes.add(themeName);
            }
        }
        return themes;
    }

    public String getConnection() {
        if (connectionOverride != null) {
            return connectionOverride;
        }
        String configured = properties.getProperty("morpheus.defaultConnection");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        Set<String> available = getAvailableConnections();
        if (available.size() == 1) {
            return available.iterator().next();
        }
        return "default_connection";
    }

    public Set<String> getAvailableConnections() {
        Set<String> connections = new HashSet<>();
        for (Object key : properties.keySet()) {
            if (key.toString().startsWith("morphium.")) {
                String connName = key.toString().split("\\.")[1];
                connections.add(connName);
            }
        }
        return connections;
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public Properties getProperties() {
        return properties;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public String getMessagingImplementation() {
        if (messagingOverride != null) {
            return messagingOverride;
        }
        return getProperty("morphium." + getConnection() + ".messaging.implementation", "single");
    }

    public MessagingConfig getMessagingConfig() {
        String conn = getConnection();
        // Queue name should be null if not set, not default to "msg"
        String queueName = getProperty("morphium." + conn + ".messaging.queueName");

        return new MessagingConfig(
            getProperty("morphium." + conn + ".messaging.processMultiple", "true").equals("true"),
            getProperty("morphium." + conn + ".messaging.multithreadded", "true").equals("true"),
            Integer.parseInt(getProperty("morphium." + conn + ".messaging.pause", "100")),
            Integer.parseInt(getProperty("morphium." + conn + ".messaging.windowSize", "10")),
            queueName,
            getProperty("morphium." + conn + ".messaging.senderId", UUID.randomUUID().toString()),
            getMessagingImplementation()
        );
    }

    public ProxyConfig getProxyConfig() {
        String conn = getConnection();
        boolean enabled = getProperty("morphium." + conn + ".proxy.enabled", "false").equals("true");
        String host = getProperty("morphium." + conn + ".proxy.host", "127.0.0.1");
        int port = Integer.parseInt(getProperty("morphium." + conn + ".proxy.port", "5555"));
        return new ProxyConfig(enabled, host, port);
    }

    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }

    public void removeProperty(String key) {
        properties.remove(key);
    }

    public void save() throws IOException {
        File configFile = new File(configFilePath);
        properties.store(new FileWriter(configFile), "Morpheus configuration");
    }

    public static class MessagingConfig {
        public final boolean processMultiple;
        public final boolean multithreadded;
        public final int pause;
        public final int windowSize;
        public final String queueName;
        public final String senderId;
        public final String implementation;

        public MessagingConfig(boolean processMultiple, boolean multithreadded, int pause,
                              int windowSize, String queueName, String senderId, String implementation) {
            this.processMultiple = processMultiple;
            this.multithreadded = multithreadded;
            this.pause = pause;
            this.windowSize = windowSize;
            this.queueName = queueName;
            this.senderId = senderId;
            this.implementation = implementation;
        }
    }

    public static class ProxyConfig {
        public final boolean enabled;
        public final String host;
        public final int port;

        public ProxyConfig(boolean enabled, String host, int port) {
            this.enabled = enabled;
            this.host = host;
            this.port = port;
        }

        public void apply() {
            if (enabled) {
                System.setProperty("socksProxyHost", host);
                System.setProperty("socksProxyPort", String.valueOf(port));
            } else {
                System.clearProperty("socksProxyHost");
                System.clearProperty("socksProxyPort");
            }
        }
    }
}

package de.caluga.morpheus.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import de.caluga.morphium.MorphiumConfig;

/**
 * Centralized configuration management for Morpheus
 */
public class ConfigurationManager {
    private final Properties properties;
    private final Map<String, String> commandArgs;
    private final String configFilePath;

    private String selectedTheme;
    private String selectedConnection;

    public ConfigurationManager() {
        this(new HashMap<>());
    }

    public ConfigurationManager(Map<String, String> commandArgs) {
        this.commandArgs = commandArgs;
        this.properties = new Properties();
        this.configFilePath = System.getProperty("user.home") + "/.config/morpheus.properties";

        loadConfiguration();
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

        // Default Morphium connection
        MorphiumConfig cfg = new MorphiumConfig();
        cfg.addHostToSeed("localhost", 27017);
        cfg.setDatabase("test");
        cfg.setMongoAuthDb("admin");
        cfg.setMongoLogin("test");
        cfg.setMongoPassword("test");
        properties.putAll(cfg.asProperties("morphium.default_connection"));

        // Default messaging settings
        properties.put("morphium.default_connection.messaging.processMultiple", "true");
        properties.put("morphium.default_connection.messaging.multithreadded", "true");
        properties.put("morphium.default_connection.messaging.windowSize", "10");
        properties.put("morphium.default_connection.messaging.pause", "100");
        properties.put("morphium.default_connection.messaging.queueName", "msg");
        properties.put("morphium.default_connection.messaging.senderId", UUID.randomUUID().toString());
        properties.put("morphium.default_connection.messaging.implementation", "single");

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
        if (selectedTheme != null) {
            return selectedTheme;
        }

        if (commandArgs.containsKey("--theme")) {
            selectedTheme = commandArgs.get("--theme");
        } else {
            selectedTheme = "default";
        }

        return selectedTheme;
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
        if (selectedConnection != null) {
            return selectedConnection;
        }

        if (commandArgs.containsKey("--morphiumcfg")) {
            selectedConnection = commandArgs.get("--morphiumcfg");
        } else {
            selectedConnection = "default_connection";
        }

        return selectedConnection;
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

    public Map<String, String> getCommandArgs() {
        return commandArgs;
    }

    public boolean isVerbose() {
        return commandArgs.containsKey("--verbose") ||
               commandArgs.getOrDefault("--verbose", "false").equalsIgnoreCase("true");
    }

    public String getMessagingImplementation() {
        String fromArgs = commandArgs.get("--messaging");
        if (fromArgs != null) {
            return fromArgs;
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

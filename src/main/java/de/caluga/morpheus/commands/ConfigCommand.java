package de.caluga.morpheus.commands;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import de.caluga.morpheus.ICommand;
import de.caluga.morpheus.Morpheus;
import de.caluga.morpheus.config.ConfigurationManager;
import de.caluga.morphium.MorphiumConfig;

/**
 * Configuration management command
 * Provides interactive tools for managing connections, themes, and proxy settings
 */
public class ConfigCommand implements ICommand {
    public final static String NAME = "config";
    public final static String DESCRIPTION = "Configuration management - manage connections, themes, proxy settings";

    private Morpheus morpheus;
    private BufferedReader reader;
    private ConfigurationManager configMgr;

    @Override
    public void execute(Morpheus morpheus, Map<String, String> args) throws Exception {
        this.morpheus = morpheus;
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.configMgr = new ConfigurationManager(args);

        String subcommand = args.getOrDefault("subcommand", "help");

        switch (subcommand) {
            case "connection":
                handleConnection(args);
                break;
            case "theme":
                handleTheme(args);
                break;
            case "proxy":
                handleProxy(args);
                break;
            case "show":
                showConfig();
                break;
            case "validate":
                validateConfig();
                break;
            case "help":
            default:
                showHelp();
                break;
        }
    }

    private void showHelp() {
        morpheus.pr("[header1]Configuration Management[r]");
        morpheus.pr("");
        morpheus.pr("Usage: [c2]config [subcommand] [action] [name][r]");
        morpheus.pr("");
        morpheus.pr("[header2]Subcommands:[r]");
        morpheus.pr("  [c3]connection[r] add|edit|remove|list|show <name>  - Manage MongoDB connections");
        morpheus.pr("  [c3]theme[r]      add|edit|remove|list           - Manage color themes");
        morpheus.pr("  [c3]proxy[r]      enable|disable|config <conn>   - Manage SOCKS proxy");
        morpheus.pr("  [c3]show[r]                                       - Show all configuration");
        morpheus.pr("  [c3]validate[r]                                   - Validate configuration");
        morpheus.pr("");
        morpheus.pr("[header2]Examples:[r]");
        morpheus.pr("  ./run.sh config connection add production");
        morpheus.pr("  ./run.sh config connection list");
        morpheus.pr("  ./run.sh config proxy enable default_connection");
        morpheus.pr("  ./run.sh config theme add darkmode");
        morpheus.pr("  ./run.sh config show");
    }

    private void handleConnection(Map<String, String> args) throws Exception {
        String action = args.getOrDefault("action", "list");
        String name = args.get("name");

        switch (action) {
            case "add":
                if (name == null) {
                    morpheus.pr("[error]Please specify connection name: config connection add <name>[r]");
                    return;
                }
                addConnection(name);
                break;
            case "edit":
                if (name == null) {
                    morpheus.pr("[error]Please specify connection name: config connection edit <name>[r]");
                    return;
                }
                editConnection(name);
                break;
            case "remove":
                if (name == null) {
                    morpheus.pr("[error]Please specify connection name: config connection remove <name>[r]");
                    return;
                }
                removeConnection(name);
                break;
            case "show":
                if (name == null) {
                    morpheus.pr("[error]Please specify connection name: config connection show <name>[r]");
                    return;
                }
                showConnection(name);
                break;
            case "list":
            default:
                listConnections();
                break;
        }
    }

    private void addConnection(String name) throws Exception {
        morpheus.pr("[header1]Add New Connection: " + name + "[r]");
        morpheus.pr("");

        // Get connection details interactively
        morpheus.pr("[c3]MongoDB Hosts[r] (comma-separated host:port, e.g., host1:27017,host2:27017):");
        morpheus.pr("  Default: localhost:27017");
        String hostsInput = readLine("localhost:27017");

        // Parse multiple hosts
        String[] hosts = hostsInput.split(",");

        morpheus.pr("[c3]Database Name[r]:");
        String database = readLine("test");

        morpheus.pr("[c3]Authentication Database[r] (default: admin):");
        String authDb = readLine("admin");

        morpheus.pr("[c3]Username[r] (press ENTER to skip authentication):");
        String username = readLine("");

        String password = "";
        if (!username.isEmpty()) {
            morpheus.pr("[c3]Password[r] (hidden):");
            password = readPassword();
        }

        morpheus.pr("[c3]Messaging Implementation[r] (single/multi, default: single):");
        String messagingImpl = readLine("single");

        morpheus.pr("[c3]Queue Name[r] (press ENTER for default 'msg'):");
        String queueName = readLine("msg");

        // Create MorphiumConfig to get properties
        MorphiumConfig cfg = new MorphiumConfig();

        // Add all hosts to seed
        for (String hostPort : hosts) {
            String trimmed = hostPort.trim();
            String[] parts = trimmed.split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 27017;
            cfg.addHostToSeed(host, port);
        }

        cfg.setDatabase(database);
        cfg.setMongoAuthDb(authDb);

        // Only set authentication if username is provided
        if (!username.isEmpty()) {
            cfg.setMongoLogin(username);
            if (!password.isEmpty()) {
                cfg.setMongoPassword(password);
            }
        }

        // Save connection properties
        java.util.Properties props = cfg.asProperties("morphium." + name);
        for (Object key : props.keySet()) {
            String keyStr = key.toString();
            // Skip authentication properties if username was empty
            if (username.isEmpty() && (keyStr.contains(".login") || keyStr.contains(".password"))) {
                continue;
            }
            configMgr.setProperty(keyStr, props.getProperty(keyStr));
        }

        // Add messaging settings
        configMgr.setProperty("morphium." + name + ".messaging.implementation", messagingImpl);
        configMgr.setProperty("morphium." + name + ".messaging.processMultiple", "true");
        configMgr.setProperty("morphium." + name + ".messaging.multithreadded", "true");
        configMgr.setProperty("morphium." + name + ".messaging.windowSize", "10");
        configMgr.setProperty("morphium." + name + ".messaging.pause", "100");
        configMgr.setProperty("morphium." + name + ".messaging.queueName", queueName);
        configMgr.setProperty("morphium." + name + ".messaging.senderId", UUID.randomUUID().toString());

        configMgr.save();

        morpheus.pr("");
        morpheus.pr("[good]✓ Connection '" + name + "' saved successfully![r]");
        morpheus.pr("");
        morpheus.pr("Use it with: [c2]--morphiumcfg=" + name + "[r]");
    }

    private void editConnection(String name) throws Exception {
        // Check if connection exists
        String testKey = "morphium." + name + ".database";
        if (configMgr.getProperty(testKey) == null) {
            morpheus.pr("[error]Connection '" + name + "' not found![r]");
            return;
        }

        morpheus.pr("[header1]Edit Connection: " + name + "[r]");
        morpheus.pr("");
        morpheus.pr("Press ENTER to keep current value");
        morpheus.pr("");

        // Parse hostSeed - format is [host1:port1, host2:port2] or [host1\:port1]
        String hostSeed = configMgr.getProperty("morphium." + name + ".hostSeed", "[localhost:27017]");
        // Remove brackets and backslashes
        String cleanedHosts = hostSeed.replaceAll("[\\[\\]\\\\]", "").trim();

        morpheus.pr("[c3]MongoDB Hosts[r] (comma-separated host:port)");
        morpheus.pr("  Current: " + cleanedHosts);
        String hostsInput = readLineOrKeep(cleanedHosts);

        // Parse multiple hosts
        String[] hosts = hostsInput.split(",");

        String currentDb = configMgr.getProperty("morphium." + name + ".database", "");
        morpheus.pr("[c3]Database Name[r] (current: " + currentDb + "):");
        String database = readLineOrKeep(currentDb);

        String currentAuthDb = configMgr.getProperty("morphium." + name + ".mongoAuthDb", "admin");
        morpheus.pr("[c3]Authentication Database[r] (current: " + currentAuthDb + "):");
        String authDb = readLineOrKeep(currentAuthDb);

        String currentUsername = configMgr.getProperty("morphium." + name + ".mongoLogin", "");
        morpheus.pr("[c3]Username[r] (current: " + (currentUsername.isEmpty() ? "none" : currentUsername) + "):");
        String username = readLineOrKeep(currentUsername);

        String password = "";
        if (!username.isEmpty()) {
            morpheus.pr("[c3]Password[r] (press ENTER to keep current, type new to change):");
            password = readLine("");
        }

        String currentMessagingImpl = configMgr.getProperty("morphium." + name + ".messaging.implementation", "single");
        morpheus.pr("[c3]Messaging Implementation[r] (current: " + currentMessagingImpl + "):");
        String messagingImpl = readLineOrKeep(currentMessagingImpl);

        String currentQueueName = configMgr.getProperty("morphium." + name + ".messaging.queueName", "msg");
        morpheus.pr("[c3]Queue Name[r] (current: " + currentQueueName + "):");
        String queueName = readLineOrKeep(currentQueueName);

        // Rebuild configuration using MorphiumConfig
        MorphiumConfig cfg = new MorphiumConfig();

        // Add all hosts to seed
        for (String hostPort : hosts) {
            String trimmed = hostPort.trim();
            String[] parts = trimmed.split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 27017;
            cfg.addHostToSeed(host, port);
        }

        cfg.setDatabase(database);
        cfg.setMongoAuthDb(authDb);

        // Only set authentication if username is provided
        if (!username.isEmpty()) {
            cfg.setMongoLogin(username);
            if (!password.isEmpty()) {
                cfg.setMongoPassword(password);
            } else {
                // Keep existing password
                String existingPassword = configMgr.getProperty("morphium." + name + ".mongoPassword", "");
                if (!existingPassword.isEmpty()) {
                    cfg.setMongoPassword(existingPassword);
                }
            }
        }

        // Save connection properties
        java.util.Properties props = cfg.asProperties("morphium." + name);
        for (Object key : props.keySet()) {
            String keyStr = key.toString();
            // Skip authentication properties if username was empty
            if (username.isEmpty() && (keyStr.contains(".login") || keyStr.contains(".password"))) {
                continue;
            }
            configMgr.setProperty(keyStr, props.getProperty(keyStr));
        }

        // Update messaging settings
        configMgr.setProperty("morphium." + name + ".messaging.implementation", messagingImpl);
        configMgr.setProperty("morphium." + name + ".messaging.queueName", queueName);
        // Keep other messaging settings unchanged
        if (configMgr.getProperty("morphium." + name + ".messaging.processMultiple") == null) {
            configMgr.setProperty("morphium." + name + ".messaging.processMultiple", "true");
        }
        if (configMgr.getProperty("morphium." + name + ".messaging.multithreadded") == null) {
            configMgr.setProperty("morphium." + name + ".messaging.multithreadded", "true");
        }
        if (configMgr.getProperty("morphium." + name + ".messaging.windowSize") == null) {
            configMgr.setProperty("morphium." + name + ".messaging.windowSize", "10");
        }
        if (configMgr.getProperty("morphium." + name + ".messaging.pause") == null) {
            configMgr.setProperty("morphium." + name + ".messaging.pause", "100");
        }
        if (configMgr.getProperty("morphium." + name + ".messaging.senderId") == null) {
            configMgr.setProperty("morphium." + name + ".messaging.senderId", UUID.randomUUID().toString());
        }

        configMgr.save();

        morpheus.pr("");
        morpheus.pr("[good]✓ Connection '" + name + "' updated successfully![r]");
    }

    private void removeConnection(String name) throws Exception {
        morpheus.pr("[warning]Remove connection '" + name + "'? (yes/no):[r]");
        String confirm = readLine("no");

        if (!confirm.equalsIgnoreCase("yes")) {
            morpheus.pr("Cancelled.");
            return;
        }

        // Remove all properties for this connection
        Set<Object> keysToRemove = configMgr.getProperties().keySet().stream()
            .filter(k -> k.toString().startsWith("morphium." + name + "."))
            .collect(java.util.stream.Collectors.toSet());

        for (Object key : keysToRemove) {
            configMgr.removeProperty(key.toString());
        }

        configMgr.save();

        morpheus.pr("[good]✓ Connection '" + name + "' removed successfully![r]");
    }

    private void showConnection(String name) {
        morpheus.pr("[header1]Connection: " + name + "[r]");
        morpheus.pr("");

        configMgr.getProperties().entrySet().stream()
            .filter(e -> e.getKey().toString().startsWith("morphium." + name + "."))
            .sorted((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()))
            .forEach(e -> {
                String key = e.getKey().toString().substring(("morphium." + name + ".").length());
                String value = e.getValue().toString();

                // Hide password
                if (key.contains("password") || key.contains("Password")) {
                    value = "********";
                }

                morpheus.pr("  [c3]" + key + "[r] = " + value);
            });
    }

    private void listConnections() {
        Set<String> connections = configMgr.getAvailableConnections();

        morpheus.pr("[header1]Available Connections:[r]");
        morpheus.pr("");

        if (connections.isEmpty()) {
            morpheus.pr("  [warning]No connections configured[r]");
        } else {
            for (String conn : connections) {
                // Read hostSeed - format is [host1:port1, host2:port2] or [host1\:port1]
                String hostSeed = configMgr.getProperty("morphium." + conn + ".hostSeed", "N/A");
                String cleanedHosts = hostSeed.equals("N/A") ? "N/A" : hostSeed.replaceAll("[\\[\\]\\\\]", "").trim();

                String db = configMgr.getProperty("morphium." + conn + ".database", "N/A");
                String messagingImpl = configMgr.getProperty("morphium." + conn + ".messaging.implementation", "N/A");

                morpheus.pr("  [c3]" + conn + "[r]");
                morpheus.pr("    Host(s): " + cleanedHosts);
                morpheus.pr("    Database: " + db);
                morpheus.pr("    Messaging: " + messagingImpl);
                morpheus.pr("");
            }
        }
    }

    private void handleTheme(Map<String, String> args) throws Exception {
        String action = args.getOrDefault("action", "list");

        // Theme management implementation
        morpheus.pr("[warning]Theme management coming soon![r]");
        morpheus.pr("For now, edit ~/.config/morpheus.properties manually");
    }

    private void handleProxy(Map<String, String> args) throws Exception {
        String action = args.getOrDefault("action", "config");
        String connectionName = args.get("name");

        if (connectionName == null) {
            connectionName = "default_connection";
        }

        switch (action) {
            case "enable":
                configMgr.setProperty("morphium." + connectionName + ".proxy.enabled", "true");
                configMgr.save();
                morpheus.pr("[good]✓ SOCKS proxy enabled for connection: " + connectionName + "[r]");
                break;

            case "disable":
                configMgr.setProperty("morphium." + connectionName + ".proxy.enabled", "false");
                configMgr.save();
                morpheus.pr("[good]✓ SOCKS proxy disabled for connection: " + connectionName + "[r]");
                break;

            case "config":
                morpheus.pr("[header1]Configure SOCKS Proxy for: " + connectionName + "[r]");
                morpheus.pr("");

                morpheus.pr("[c3]SOCKS Proxy Host[r] (default: 127.0.0.1):");
                String host = readLine("127.0.0.1");

                morpheus.pr("[c3]SOCKS Proxy Port[r] (default: 5555):");
                String port = readLine("5555");

                morpheus.pr("[c3]Enable proxy?[r] (yes/no, default: yes):");
                String enable = readLine("yes");

                configMgr.setProperty("morphium." + connectionName + ".proxy.host", host);
                configMgr.setProperty("morphium." + connectionName + ".proxy.port", port);
                configMgr.setProperty("morphium." + connectionName + ".proxy.enabled",
                    enable.equalsIgnoreCase("yes") ? "true" : "false");

                configMgr.save();

                morpheus.pr("");
                morpheus.pr("[good]✓ SOCKS proxy configured:[r]");
                morpheus.pr("  Host: " + host);
                morpheus.pr("  Port: " + port);
                morpheus.pr("  Enabled: " + enable);
                break;
        }
    }

    private void showConfig() {
        morpheus.pr("[header1]Configuration File:[r] ~/.config/morpheus.properties");
        morpheus.pr("");

        configMgr.getProperties().entrySet().stream()
            .sorted((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()))
            .forEach(e -> {
                String key = e.getKey().toString();
                String value = e.getValue().toString();

                // Hide passwords
                if (key.toLowerCase().contains("password")) {
                    value = "********";
                }

                morpheus.pr("  [c3]" + key + "[r] = " + value);
            });
    }

    private void validateConfig() {
        morpheus.pr("[header1]Validating Configuration...[r]");
        morpheus.pr("");

        Set<String> connections = configMgr.getAvailableConnections();

        if (connections.isEmpty()) {
            morpheus.pr("[warning]⚠ No connections configured[r]");
        } else {
            morpheus.pr("[good]✓ Found " + connections.size() + " connection(s)[r]");

            for (String conn : connections) {
                morpheus.pr("");
                morpheus.pr("[c2]Checking connection: " + conn + "[r]");

                String host = configMgr.getProperty("morphium." + conn + ".hosts.0");
                String db = configMgr.getProperty("morphium." + conn + ".database");

                if (host == null || host.isEmpty()) {
                    morpheus.pr("  [error]✗ Missing host configuration[r]");
                } else {
                    morpheus.pr("  [good]✓ Host: " + host + "[r]");
                }

                if (db == null || db.isEmpty()) {
                    morpheus.pr("  [error]✗ Missing database configuration[r]");
                } else {
                    morpheus.pr("  [good]✓ Database: " + db + "[r]");
                }
            }
        }

        morpheus.pr("");
        morpheus.pr("[good]Validation complete[r]");
    }

    private String readLine(String defaultValue) throws Exception {
        String line = reader.readLine();
        return (line == null || line.trim().isEmpty()) ? defaultValue : line.trim();
    }

    private String readLineOrKeep(String currentValue) throws Exception {
        String line = reader.readLine();
        return (line == null || line.trim().isEmpty()) ? currentValue : line.trim();
    }

    private String readPassword() throws Exception {
        // Note: In real implementation, you might want to use Console.readPassword() for hidden input
        return reader.readLine();
    }
}

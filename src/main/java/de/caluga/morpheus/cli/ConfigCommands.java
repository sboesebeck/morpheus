package de.caluga.morpheus.cli;

import de.caluga.morpheus.config.ConfigurationManager;
import de.caluga.morphium.MorphiumConfig;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(name = "config", description = "Manage connections, themes and proxy settings.",
         mixinStandardHelpOptions = true,
         subcommands = {
             ConfigCommands.Connection.class,
             ConfigCommands.Theme.class,
             ConfigCommands.Proxy.class,
             ConfigCommands.Show.class,
             ConfigCommands.Validate.class
         })
public class ConfigCommands implements Callable<Integer> {

    @ParentCommand RootCommand parent;

    @Override
    public Integer call() {
        // no subcommand -> show help for the config command
        CommandLine cl = new CommandLine(this);
        cl.usage(cl.getOut());
        return 0;
    }

    /** Persists the default connection used when -c is not given. Extracted for testability. */
    public static void setDefaultConnection(ConfigurationManager cm, String name) throws Exception {
        cm.setProperty("morpheus.defaultConnection", name);
        cm.save();
    }

    // ── input helpers ─────────────────────────────────────────────────────────

    private static String readLine(String defaultValue) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line = reader.readLine();
        return (line == null || line.trim().isEmpty()) ? defaultValue : line.trim();
    }

    private static String readLineOrKeep(String currentValue) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line = reader.readLine();
        return (line == null || line.trim().isEmpty()) ? currentValue : line.trim();
    }

    private static String readPassword() throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        return reader.readLine();
    }

    // ── subcommands ───────────────────────────────────────────────────────────

    @Command(name = "connection", description = "Manage MongoDB connections.",
             mixinStandardHelpOptions = true)
    public static class Connection implements Callable<Integer> {
        @ParentCommand ConfigCommands parent;

        @Parameters(index = "0", description = "Action: add, edit, remove, list, show, default.")
        String action;

        @Parameters(index = "1", arity = "0..1", description = "Connection name.")
        String name;

        @Override
        public Integer call() throws Exception {
            MorpheusContext ctx = parent.parent.context();
            ConfigurationManager cm = ctx.getConfig();
            switch (action) {
                case "add"     -> { requireName(ctx); addConnection(ctx, cm, name); }
                case "edit"    -> { requireName(ctx); editConnection(ctx, cm, name); }
                case "remove"  -> { requireName(ctx); removeConnection(ctx, cm, name); }
                case "show"    -> { requireName(ctx); showConnection(ctx, cm, name); }
                case "default" -> {
                    requireName(ctx);
                    setDefaultConnection(cm, name);
                    ctx.pr("[good]Default connection set to '" + name + "'[r]");
                }
                case "list"    -> listConnections(ctx, cm);
                default -> {
                    ctx.pr("[error]Unknown action: " + action + "[r]");
                    return 2;
                }
            }
            return 0;
        }

        private void requireName(MorpheusContext ctx) {
            if (name == null) {
                ctx.pr("[error]Please specify a connection name.[r]");
                throw new CommandLine.ParameterException(
                    new CommandLine(this), "Missing connection name");
            }
        }

        private static void addConnection(MorpheusContext ctx, ConfigurationManager cm, String name) throws Exception {
            ctx.pr("[header1]Add New Connection: " + name + "[r]");
            ctx.pr("");

            ctx.pr("[c3]MongoDB Hosts[r] (comma-separated host:port, e.g., host1:27017,host2:27017):");
            ctx.pr("  Default: localhost:27017");
            String hostsInput = readLine("localhost:27017");

            String[] hosts = hostsInput.split(",");

            ctx.pr("[c3]Database Name[r]:");
            String database = readLine("test");

            ctx.pr("[c3]Authentication Database[r] (default: admin):");
            String authDb = readLine("admin");

            ctx.pr("[c3]Username[r] (press ENTER to skip authentication):");
            String username = readLine("");

            String password = "";
            if (!username.isEmpty()) {
                ctx.pr("[c3]Password[r] (hidden):");
                password = readPassword();
            }

            ctx.pr("[c3]Messaging Implementation[r] (single/multi, default: single):");
            String messagingImpl = readLine("single");

            ctx.pr("[c3]Queue Name[r] (press ENTER for default 'msg'):");
            String queueName = readLine("msg");

            MorphiumConfig cfg = new MorphiumConfig();

            for (String hostPort : hosts) {
                String trimmed = hostPort.trim();
                String[] parts = trimmed.split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 27017;
                cfg.addHostToSeed(host, port);
            }

            cfg.setDatabase(database);
            cfg.setMongoAuthDb(authDb);

            if (!username.isEmpty()) {
                cfg.setMongoLogin(username);
                if (!password.isEmpty()) {
                    cfg.setMongoPassword(password);
                }
            }

            Properties props = cfg.asProperties("morphium." + name);
            for (Object key : props.keySet()) {
                String keyStr = key.toString();
                if (username.isEmpty() && (keyStr.contains(".login") || keyStr.contains(".password"))) {
                    continue;
                }
                cm.setProperty(keyStr, props.getProperty(keyStr));
            }

            cm.setProperty("morphium." + name + ".messaging.implementation", messagingImpl);
            cm.setProperty("morphium." + name + ".messaging.processMultiple", "true");
            cm.setProperty("morphium." + name + ".messaging.multithreadded", "true");
            cm.setProperty("morphium." + name + ".messaging.windowSize", "10");
            cm.setProperty("morphium." + name + ".messaging.pause", "100");
            cm.setProperty("morphium." + name + ".messaging.queueName", queueName);
            cm.setProperty("morphium." + name + ".messaging.senderId", UUID.randomUUID().toString());

            cm.save();

            ctx.pr("");
            ctx.pr("[good]✓ Connection '" + name + "' saved successfully![r]");
            ctx.pr("");
            ctx.pr("Use it with: [c2]-c " + name + "[r]");
        }

        private static void editConnection(MorpheusContext ctx, ConfigurationManager cm, String name) throws Exception {
            String testKey = "morphium." + name + ".database";
            if (cm.getProperty(testKey) == null) {
                ctx.pr("[error]Connection '" + name + "' not found![r]");
                return;
            }

            ctx.pr("[header1]Edit Connection: " + name + "[r]");
            ctx.pr("");
            ctx.pr("Press ENTER to keep current value");
            ctx.pr("");

            String hostSeed = cm.getProperty("morphium." + name + ".hostSeed", "[localhost:27017]");
            String cleanedHosts = hostSeed.replaceAll("[\\[\\]\\\\]", "").trim();

            ctx.pr("[c3]MongoDB Hosts[r] (comma-separated host:port)");
            ctx.pr("  Current: " + cleanedHosts);
            String hostsInput = readLineOrKeep(cleanedHosts);

            String[] hosts = hostsInput.split(",");

            String currentDb = cm.getProperty("morphium." + name + ".database", "");
            ctx.pr("[c3]Database Name[r] (current: " + currentDb + "):");
            String database = readLineOrKeep(currentDb);

            String currentAuthDb = cm.getProperty("morphium." + name + ".mongoAuthDb", "admin");
            ctx.pr("[c3]Authentication Database[r] (current: " + currentAuthDb + "):");
            String authDb = readLineOrKeep(currentAuthDb);

            String currentUsername = cm.getProperty("morphium." + name + ".mongoLogin", "");
            ctx.pr("[c3]Username[r] (current: " + (currentUsername.isEmpty() ? "none" : currentUsername) + "):");
            String username = readLineOrKeep(currentUsername);

            String password = "";
            if (!username.isEmpty()) {
                ctx.pr("[c3]Password[r] (press ENTER to keep current, type new to change):");
                password = readLine("");
            }

            String currentMessagingImpl = cm.getProperty("morphium." + name + ".messaging.implementation", "single");
            ctx.pr("[c3]Messaging Implementation[r] (current: " + currentMessagingImpl + "):");
            String messagingImpl = readLineOrKeep(currentMessagingImpl);

            String currentQueueName = cm.getProperty("morphium." + name + ".messaging.queueName", "msg");
            ctx.pr("[c3]Queue Name[r] (current: " + currentQueueName + "):");
            String queueName = readLineOrKeep(currentQueueName);

            MorphiumConfig cfg = new MorphiumConfig();

            for (String hostPort : hosts) {
                String trimmed = hostPort.trim();
                String[] parts = trimmed.split(":");
                String host = parts[0];
                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 27017;
                cfg.addHostToSeed(host, port);
            }

            cfg.setDatabase(database);
            cfg.setMongoAuthDb(authDb);

            if (!username.isEmpty()) {
                cfg.setMongoLogin(username);
                if (!password.isEmpty()) {
                    cfg.setMongoPassword(password);
                } else {
                    String existingPassword = cm.getProperty("morphium." + name + ".mongoPassword", "");
                    if (!existingPassword.isEmpty()) {
                        cfg.setMongoPassword(existingPassword);
                    }
                }
            }

            Properties props = cfg.asProperties("morphium." + name);
            for (Object key : props.keySet()) {
                String keyStr = key.toString();
                if (username.isEmpty() && (keyStr.contains(".login") || keyStr.contains(".password"))) {
                    continue;
                }
                cm.setProperty(keyStr, props.getProperty(keyStr));
            }

            cm.setProperty("morphium." + name + ".messaging.implementation", messagingImpl);
            cm.setProperty("morphium." + name + ".messaging.queueName", queueName);
            if (cm.getProperty("morphium." + name + ".messaging.processMultiple") == null) {
                cm.setProperty("morphium." + name + ".messaging.processMultiple", "true");
            }
            if (cm.getProperty("morphium." + name + ".messaging.multithreadded") == null) {
                cm.setProperty("morphium." + name + ".messaging.multithreadded", "true");
            }
            if (cm.getProperty("morphium." + name + ".messaging.windowSize") == null) {
                cm.setProperty("morphium." + name + ".messaging.windowSize", "10");
            }
            if (cm.getProperty("morphium." + name + ".messaging.pause") == null) {
                cm.setProperty("morphium." + name + ".messaging.pause", "100");
            }
            if (cm.getProperty("morphium." + name + ".messaging.senderId") == null) {
                cm.setProperty("morphium." + name + ".messaging.senderId", UUID.randomUUID().toString());
            }

            cm.save();

            ctx.pr("");
            ctx.pr("[good]✓ Connection '" + name + "' updated successfully![r]");
        }

        private static void removeConnection(MorpheusContext ctx, ConfigurationManager cm, String name) throws Exception {
            ctx.pr("[warning]Remove connection '" + name + "'? (yes/no):[r]");
            String confirm = readLine("no");

            if (!confirm.equalsIgnoreCase("yes")) {
                ctx.pr("Cancelled.");
                return;
            }

            Set<Object> keysToRemove = cm.getProperties().keySet().stream()
                .filter(k -> k.toString().startsWith("morphium." + name + "."))
                .collect(Collectors.toSet());

            for (Object key : keysToRemove) {
                cm.removeProperty(key.toString());
            }

            cm.save();

            ctx.pr("[good]✓ Connection '" + name + "' removed successfully![r]");
        }

        private static void showConnection(MorpheusContext ctx, ConfigurationManager cm, String name) {
            ctx.pr("[header1]Connection: " + name + "[r]");
            ctx.pr("");

            cm.getProperties().entrySet().stream()
                .filter(e -> e.getKey().toString().startsWith("morphium." + name + "."))
                .sorted((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()))
                .forEach(e -> {
                    String key = e.getKey().toString().substring(("morphium." + name + ".").length());
                    String value = e.getValue().toString();
                    if (key.contains("password") || key.contains("Password")) {
                        value = "********";
                    }
                    ctx.pr("  [c3]" + key + "[r] = " + value);
                });
        }

        private static void listConnections(MorpheusContext ctx, ConfigurationManager cm) {
            Set<String> connections = cm.getAvailableConnections();

            ctx.pr("[header1]Available Connections:[r]");
            ctx.pr("");

            if (connections.isEmpty()) {
                ctx.pr("  [warning]No connections configured[r]");
            } else {
                for (String conn : connections) {
                    String hostSeed = cm.getProperty("morphium." + conn + ".hostSeed", "N/A");
                    String cleanedHosts = hostSeed.equals("N/A") ? "N/A" : hostSeed.replaceAll("[\\[\\]\\\\]", "").trim();
                    String db = cm.getProperty("morphium." + conn + ".database", "N/A");
                    String messagingImpl = cm.getProperty("morphium." + conn + ".messaging.implementation", "N/A");

                    ctx.pr("  [c3]" + conn + "[r]");
                    ctx.pr("    Host(s): " + cleanedHosts);
                    ctx.pr("    Database: " + db);
                    ctx.pr("    Messaging: " + messagingImpl);
                    ctx.pr("");
                }
            }
        }
    }

    @Command(name = "theme", description = "Manage themes.", mixinStandardHelpOptions = true)
    public static class Theme implements Callable<Integer> {
        @ParentCommand ConfigCommands parent;

        @Parameters(index = "0", arity = "0..1", defaultValue = "list",
                    description = "Action: list.")
        String action;

        @Override
        public Integer call() {
            MorpheusContext ctx = parent.parent.context();
            ctx.pr("[header1]Available themes:[r]");
            for (String t : ctx.getConfig().getAvailableThemes()) {
                ctx.pr("  • " + t);
            }
            return 0;
        }
    }

    @Command(name = "proxy", description = "Manage SOCKS proxy per connection.",
             mixinStandardHelpOptions = true)
    public static class Proxy implements Callable<Integer> {
        @ParentCommand ConfigCommands parent;

        @Parameters(index = "0", description = "Action: enable, disable, config.")
        String action;

        @Parameters(index = "1", arity = "0..1", description = "Connection name.")
        String name;

        @Override
        public Integer call() throws Exception {
            MorpheusContext ctx = parent.parent.context();
            ConfigurationManager cm = ctx.getConfig();

            String connectionName = (name != null) ? name : cm.getConnection();

            switch (action) {
                case "enable" -> {
                    cm.setProperty("morphium." + connectionName + ".proxy.enabled", "true");
                    cm.save();
                    ctx.pr("[good]✓ SOCKS proxy enabled for connection: " + connectionName + "[r]");
                }
                case "disable" -> {
                    cm.setProperty("morphium." + connectionName + ".proxy.enabled", "false");
                    cm.save();
                    ctx.pr("[good]✓ SOCKS proxy disabled for connection: " + connectionName + "[r]");
                }
                case "config" -> {
                    ctx.pr("[header1]Configure SOCKS Proxy for: " + connectionName + "[r]");
                    ctx.pr("");

                    ctx.pr("[c3]SOCKS Proxy Host[r] (default: 127.0.0.1):");
                    String host = readLine("127.0.0.1");

                    ctx.pr("[c3]SOCKS Proxy Port[r] (default: 5555):");
                    String port = readLine("5555");

                    ctx.pr("[c3]Enable proxy?[r] (yes/no, default: yes):");
                    String enable = readLine("yes");

                    cm.setProperty("morphium." + connectionName + ".proxy.host", host);
                    cm.setProperty("morphium." + connectionName + ".proxy.port", port);
                    cm.setProperty("morphium." + connectionName + ".proxy.enabled",
                        enable.equalsIgnoreCase("yes") ? "true" : "false");

                    cm.save();

                    ctx.pr("");
                    ctx.pr("[good]✓ SOCKS proxy configured:[r]");
                    ctx.pr("  Host: " + host);
                    ctx.pr("  Port: " + port);
                    ctx.pr("  Enabled: " + enable);
                }
                default -> {
                    ctx.pr("[error]Unknown proxy action: " + action + "[r]");
                    return 2;
                }
            }
            return 0;
        }
    }

    @Command(name = "show", description = "Show the full configuration (passwords masked).",
             mixinStandardHelpOptions = true)
    public static class Show implements Callable<Integer> {
        @ParentCommand ConfigCommands parent;

        @Override
        public Integer call() {
            MorpheusContext ctx = parent.parent.context();
            ConfigurationManager cm = ctx.getConfig();

            ctx.pr("[header1]Configuration File:[r] ~/.config/morpheus.properties");
            ctx.pr("");

            cm.getProperties().entrySet().stream()
                .sorted((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()))
                .forEach(e -> {
                    String key = e.getKey().toString();
                    String value = e.getValue().toString();
                    if (key.toLowerCase().contains("password")) {
                        value = "********";
                    }
                    ctx.pr("  [c3]" + key + "[r] = " + value);
                });

            return 0;
        }
    }

    @Command(name = "validate", description = "Validate the configuration.",
             mixinStandardHelpOptions = true)
    public static class Validate implements Callable<Integer> {
        @ParentCommand ConfigCommands parent;

        @Override
        public Integer call() {
            MorpheusContext ctx = parent.parent.context();
            ConfigurationManager cm = ctx.getConfig();

            ctx.pr("[header1]Validating Configuration...[r]");
            ctx.pr("");

            Set<String> connections = cm.getAvailableConnections();

            if (connections.isEmpty()) {
                ctx.pr("[warning]⚠ No connections configured[r]");
            } else {
                ctx.pr("[good]✓ Found " + connections.size() + " connection(s)[r]");

                for (String conn : connections) {
                    ctx.pr("");
                    ctx.pr("[c2]Checking connection: " + conn + "[r]");

                    String host = cm.getProperty("morphium." + conn + ".hostSeed");
                    String db = cm.getProperty("morphium." + conn + ".database");

                    if (host == null || host.isEmpty()) {
                        ctx.pr("  [error]✗ Missing host configuration[r]");
                    } else {
                        ctx.pr("  [good]✓ Host: " + host + "[r]");
                    }

                    if (db == null || db.isEmpty()) {
                        ctx.pr("  [error]✗ Missing database configuration[r]");
                    } else {
                        ctx.pr("  [good]✓ Database: " + db + "[r]");
                    }
                }
            }

            ctx.pr("");
            ctx.pr("[good]Validation complete[r]");

            return 0;
        }
    }
}

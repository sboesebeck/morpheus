package de.caluga.morpheus;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.caluga.morpheus.commands.*;
import de.caluga.morpheus.config.ConfigurationManager;
import de.caluga.morpheus.connection.MorphiumConnectionFactory;
import de.caluga.morpheus.rendering.ThemeManager;
import de.caluga.morpheus.utils.TerminalUtils;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.messaging.MorphiumMessaging;

/**
 * Main entry point for Morpheus - Morphium Toolbox and Messaging Monitor
 * Refactored for cleaner architecture with ConfigurationManager, ThemeManager, and MorphiumConnectionFactory
 */
public class Morpheus {
    private static final Map<String, Class<? extends ICommand>> commands = new HashMap<>();

    static {
        // Register commands
        commands.put(HelloCommand.NAME, HelloCommand.class);
        commands.put(ListCommands.NAME, ListCommands.class);
        commands.put(GetStatus.NAME, GetStatus.class);
        commands.put(MessageMonitor.NAME, MessageMonitor.class);
        commands.put(SendMessageCommand.NAME, SendMessageCommand.class);
        commands.put(ConfigCommand.NAME, ConfigCommand.class);
    }

    private final ConfigurationManager config;
    private final ThemeManager theme;
    private Morphium morphium;
    private MorphiumMessaging messaging;

    public Morpheus(Map<String, String> commandArgs) {
        this.config = new ConfigurationManager(commandArgs);
        this.theme = new ThemeManager(config);
    }

    public Morphium getMorphium() {
        return morphium;
    }

    public MorphiumMessaging getMessaging() {
        return messaging;
    }

    public ThemeManager getTheme() {
        return theme;
    }

    // Backward compatibility methods for commands
    public void pr(String str) {
        theme.print(str);
    }

    public void pr(String str, int themeGradientNr) {
        theme.print(str, themeGradientNr);
    }

    public void pr(String str, Gradient gradient) {
        theme.print(str, gradient.toThemeGradient());
    }

    public String getColumn(String str, int len) {
        return theme.getColumn(str, len);
    }

    public String getAnsiString(String str) {
        return theme.render(str);
    }

    public String getAnsiFGColor(int colNum) {
        return theme.getAnsiFGColor(colNum);
    }

    public String getAnsiBGColor(int colNum) {
        return theme.getAnsiBGColor(colNum);
    }

    public String ansiReset() {
        return theme.ansiReset();
    }

    // For backward compatibility - expose Gradient enum
    public enum Gradient {
        blue, cyan, grey, green, red, yellow, purple;

        public ThemeManager.Gradient toThemeGradient() {
            return ThemeManager.Gradient.valueOf(this.name());
        }
    }

    public String getNameFromCommandClass(Class<? extends ICommand> commandClass) throws NoSuchFieldException, IllegalAccessException {
        Field nameField = commandClass.getDeclaredField("NAME");
        return (String) nameField.get(null);
    }

    public String getDocumentationFromCommandClass(Class<? extends ICommand> commandClass) {
        try {
            Field description = commandClass.getDeclaredField("DESCRIPTION");
            return (String) description.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    public Set<Class<? extends ICommand>> getCommandClasses() {
        return new HashSet<>(commands.values());
    }

    private void printBanner() {
        String col1 = theme.getAnsiFGColor(241);
        String col2 = theme.getAnsiFGColor(245);
        String col3 = theme.getAnsiFGColor(252);
        String bg = theme.getAnsiBGColor(236);
        String reset = theme.ansiReset();

        System.out.println(bg + col2 + "   __  __                  _                          " + reset);
        System.out.println(bg + col2 + "  |  \\/  | ___  _ __ _ __ | |__   ___ _   _ ___       " + reset);
        System.out.println(bg + col3 + "  | |\\/| |/ _ \\| '__| '_ \\| '_ \\ / _ \\ | | / __|      " + reset);
        System.out.println(bg + col3 + "  | |  | | (_) | |  | |_) | | | |  __/ |_| \\__ \\      " + reset);
        System.out.println(bg + col2 + "  |_|  |_|\\___/|_|  | .__/|_| |_|\\___|\\__,_|___/      " + reset);
        System.out.println(bg + col2 + "                    |_|                               " + reset);
        System.out.println(col1 + "  Version: " + Version.VERSION + reset);
        System.out.println();
    }

    private void printUsage() {
        printBanner();
        theme.print("[rd]Usage:[r] [bld]Morpheus[r] [b]<commandName>[r] [options] [arguments]");
        theme.print("");

        // List all available commands
        theme.print("[header1]Available Commands:[r]");
        theme.print("");

        // Collect and sort commands
        java.util.List<CommandInfo> commandInfos = new java.util.ArrayList<>();
        for (Class<? extends ICommand> commandClass : getCommandClasses()) {
            try {
                String name = getNameFromCommandClass(commandClass);
                String description = getDocumentationFromCommandClass(commandClass);
                if (description == null) {
                    description = "No description available";
                }
                commandInfos.add(new CommandInfo(name, description));
            } catch (Exception e) {
                // Skip commands that can't be introspected
            }
        }

        // Sort commands alphabetically
        commandInfos.sort((a, b) -> a.name.compareTo(b.name));

        // Print commands with aligned descriptions
        int maxNameLength = commandInfos.stream()
            .mapToInt(c -> c.name.length())
            .max()
            .orElse(15);

        for (CommandInfo cmd : commandInfos) {
            String padding = " ".repeat(Math.max(0, maxNameLength - cmd.name.length()));
            theme.print("  [c2]" + cmd.name + "[r]" + padding + "  " + cmd.description);
        }

        theme.print("");
        theme.print("[header1]Global Options:[r]");
        theme.print("  [c2]--theme=<name>[r]        Select theme (use --theme=? to list)");
        theme.print("  [c2]--morphiumcfg=<name>[r]  Select connection (use --morphiumcfg=? to list)");
        theme.print("  [c2]--messaging=<type>[r]    Messaging implementation: single|multi");
        theme.print("  [c2]--verbose[r]             Show detailed startup information");
        theme.print("");
        theme.print("[header1]Examples:[r]");
        theme.print("  [c3]./run.sh list[r]                           # List all commands");
        theme.print("  [c3]./run.sh config connection add mydb[r]     # Add a new connection");
        theme.print("  [c3]./run.sh get_status --morphiumcfg=mydb[r]  # Query status from mydb");
        theme.print("  [c3]./run.sh monitor --theme=darkmode[r]       # Monitor messages with theme");
        theme.print("");
        theme.print("Configuration file: [ul]~/.config/morpheus.properties[r]");
        theme.print("For detailed help on a command, run: [c2]./run.sh <command> --help[r]");
    }

    // Helper class for command info
    private static class CommandInfo {
        final String name;
        final String description;

        CommandInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    public void run(String[] args) {
        try {
            if (args.length < 1) {
                printUsage();
                return;
            }

            String commandName = args[0];

            if (commandName.startsWith("-")) {
                theme.print("[rd]Commands do not start with - -- maybe try 'list'?[r]");
                System.exit(1);
            }

            // Handle special arguments
            if (handleSpecialArguments()) {
                return;
            }

            // Show banner and config summary
            if (!config.isVerbose()) {
                printBanner();
            } else {
                printBanner();
                theme.print("Configuration:");
                theme.print("  Theme: [c2]" + config.getTheme() + "[r]");
                theme.print("  Connection: [c2]" + config.getConnection() + "[r]");
                theme.print("  Terminal: [c2]" + TerminalUtils.getTerminalSize(true) + "[r]");
            }

            // Connect to Morphium
            MorphiumConnectionFactory factory = new MorphiumConnectionFactory(config, theme);
            morphium = factory.createMorphium();
            messaging = factory.createMessaging(morphium);

            if (config.isVerbose()) {
                theme.print("");
            }

            // Execute command
            executeCommand(commandName);

        } catch (Exception e) {
            theme.print("[error]Error: " + e.getMessage() + "[r]");
            if (config.isVerbose()) {
                e.printStackTrace();
            }
            System.exit(1);
        } finally {
            cleanup();
        }
    }

    private boolean handleSpecialArguments() {
        // Theme listing
        if (config.getCommandArgs().containsKey("--theme") &&
            (config.getCommandArgs().get("--theme").equals("?") ||
             config.getCommandArgs().get("--theme").equals("list"))) {
            theme.print("=========== Configured themes: ===========", ThemeManager.Gradient.green);
            for (String themeName : config.getAvailableThemes()) {
                theme.print("  • " + themeName);
            }
            return true;
        }

        // Connection listing
        if (config.getCommandArgs().containsKey("--morphiumcfg") &&
            (config.getCommandArgs().get("--morphiumcfg").equals("?") ||
             config.getCommandArgs().get("--morphiumcfg").equals("list"))) {
            theme.print("=========== Configured connections: ===========", ThemeManager.Gradient.green);
            int i = 1;
            Map<String, String> connMap = new HashMap<>();
            for (String connName : config.getAvailableConnections()) {
                theme.print("[good]" + i + "[r]. [header1]" + connName + "[r]");
                connMap.put("" + i, connName);
                i++;
            }

            theme.print("");
            theme.print("[c1]Choose connection or x to quit....[r]");
            try {
                int input = System.in.read();
                char c = (char) input;

                if (!connMap.containsKey("" + c)) {
                    theme.print("[c2]Aborting[r]");
                    return true;
                }

                theme.print("[good]You chose: " + c + " -> [c2]" + connMap.get("" + c) + "[r]");
                // Note: Would need to reinitialize with new connection
            } catch (Exception e) {
                theme.print("[error]Error reading input[r]");
            }
            return true;
        }

        return false;
    }

    private void executeCommand(String commandName) {
        try {
            for (Class<? extends ICommand> commandClass : getCommandClasses()) {
                String name = getNameFromCommandClass(commandClass);
                if (name.equals(commandName)) {
                    ICommand command = commandClass.getDeclaredConstructor().newInstance();
                    command.execute(this, config.getCommandArgs());
                    return;
                }
            }

            throw new IllegalArgumentException("Unknown command: " + commandName);
        } catch (Exception e) {
            theme.print("[error]Failed to execute command: " + e.getMessage() + "[r]");
            if (config.isVerbose()) {
                e.printStackTrace();
            }
            printUsage();
        }
    }

    private void cleanup() {
        try {
            if (morphium != null) {
                morphium.close();
            }
            if (messaging != null) {
                messaging.terminate();
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    public static void main(String[] args) {
        Map<String, String> commandArgs = parseCommandArgs(args);
        Morpheus app = new Morpheus(commandArgs);
        app.run(args);
    }

    private static Map<String, String> parseCommandArgs(String[] args) {
        Map<String, String> commandArgs = new HashMap<>();

        // Special handling for config command with positional arguments
        if (args.length > 0 && args[0].equals("config")) {
            if (args.length > 1) commandArgs.put("subcommand", args[1]);
            if (args.length > 2) commandArgs.put("action", args[2]);
            if (args.length > 3) commandArgs.put("name", args[3]);

            // Also parse any remaining key=value args
            for (int i = 4; i < args.length; i++) {
                if (args[i].contains("=")) {
                    String[] splitArg = args[i].split("=", 2);
                    commandArgs.put(splitArg[0], splitArg[1]);
                } else {
                    commandArgs.put(args[i], "true");
                }
            }
        } else {
            // Standard parsing for other commands
            for (int i = 1; i < args.length; i++) {
                if (args[i].contains("=")) {
                    String[] splitArg = args[i].split("=", 2);
                    commandArgs.put(splitArg[0], splitArg[1]);
                } else {
                    // Handle flags without values
                    commandArgs.put(args[i], "true");
                }
            }
        }

        return commandArgs;
    }
}

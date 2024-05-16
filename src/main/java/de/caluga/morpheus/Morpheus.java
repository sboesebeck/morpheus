package de.caluga.morpheus;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import de.caluga.morpheus.commands.GetStatus;
import de.caluga.morpheus.commands.HelloCommand;
import de.caluga.morpheus.commands.ListCommands;
import de.caluga.morpheus.commands.MessageMonitor;
import de.caluga.morpheus.commands.SendMessageCommand;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import de.caluga.morphium.MorphiumConfig.CappedCheck;
import de.caluga.morphium.MorphiumConfig.IndexCheck;
import de.caluga.morphium.driver.ReadPreference;
import de.caluga.morphium.driver.wire.PooledDriver;
import de.caluga.morphium.messaging.Messaging;

public class Morpheus {
    public static class Size {
        private int col, row;

        public Size(final int col, final int row) {
            this.col = col;
            this.row = row;
        }

        public int getCol() {
            return col;
        }

        public void setCol(final int col) {
            this.col = col;
        }

        public int getRow() {
            return row;
        }

        public void setRow(final int row) {
            this.row = row;
        }

        @Override
        public String toString() {
            return "col=" + col + ", row=" + row;
        }


    }

    public enum Gradient {
        blue, cyan, grey, green, red, yellow, purple,
    }
    public final static String COMMANDS_PACKAGE = "de.caluga.morpheus.commands.";
    private static String ANSI_RESET = "\u001B[0m";
    private static String ANSI_BLACK = "\u001B[30m";
    private static String ANSI_RED = "\u001B[31m";
    private static String ANSI_GREEN = "\u001B[32m";
    private static String ANSI_BLUE = "\u001B[34m";
    private static String ANSI_YELLOW = "\u001B[33m";
    private static String ANSI_MAGENTA = "\u001B[35m";

    private static String ANSI_CYAN = "\u001B[36m";
    private static String ANSI_WHITE = "\u001B[37m";
    private static String ANSI_BG_BLACK = "\u001B[40m";
    private static String ANSI_BG_RED = "\u001B[41m";
    private static String ANSI_BG_GREEN = "\u001B[42m";
    private static String ANSI_BG_BLUE = "\u001B[44m";
    private static String ANSI_BG_YELLOW = "\u001B[43m";
    private static String ANSI_BG_MAGENTA = "\u001B[45m";

    private static String ANSI_BG_CYAN = "\u001B[46m";
    private static String ANSI_BG_WHITE = "\u001B[47m";
    private static String ANSI_BOLD = "\u001B[1m";
    private static String ANSI_FAINT = "\u001B[2m";
    private static String ANSI_ITALIC = "\u001B[3m";
    private static String ANSI_UNDERLINE = "\u001B[4m";
    private static String ANSI_SLOW_BLINK = "\u001B[5m";
    private static String ANSI_FAST_BLINK = "\u001B[6m";
    private static String ANSI_INVERT = "\u001B[7m";

    private static String ANSI_HIDDEN = "\u001B[8m";
    private static String ANSI_STRIKETHROUGH = "\u001B[9m";
    public static Map<String, Class<? extends ICommand>> commands = new HashMap<>();
    public static void main(final String args[]) throws Exception {
        final var app = new Morpheus();
        app.registerAnsiCodes();

        if (args.length < 1) {
            app.printUsage();
            return;
        }

        app.runApp(args);
    }

    public static Size getTerminalSize() {
        try {
            final String[] cmd = {"/bin/sh", "-c", "tput cols && tput lines"};
            final Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor();
            // Read the terminal response
            final byte[] input = new byte[32];
            final int size = process.getInputStream().read(input);
            final String[] output = new String(input, 0, size).split("\\s+");
            final int cols = Integer.parseInt(output[0]);
            final int rows = Integer.parseInt(output[1]);
            // // Request the terminal size
            // System.out.print("\u001B[7;9999H\u001B[6n");
            // System.out.flush();
            // // Read the terminal response
            // byte[] input = new byte[32];
            // int size = System.in.read(input);
            // // Extract the row and column size from the response
            // int row = 0;
            // int col = 0;
            // int i = 0;
            //
            // while (input[i] != 'R') {
            //     if (input[i] == '[') {
            //         i++;
            //         int j = i;
            //
            //         while (input[j] != ';') {
            //             j++;
            //         }
            //
            //         row = Integer.parseInt(new String(input, i, j - i));
            //         i = j + 1;
            //     } else {
            //         i++;
            //     }
            // }
            //
            // i++;
            // int j = i;
            //
            // while (input[j] != ';') {
            //     j++;
            // }
            //
            // col = Integer.parseInt(new String(input, i, j - i));
            return new Size(cols, rows);
        } catch (final Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static void moveCursor(final int row, final int col) {
        System.out.print("\u001B[" + row + ";" + col + "H");
    }

    private void printUsage() {
        final String col1 = getAnsiFGColor(241);
        final String col2 = getAnsiFGColor(245);
        final String col3 = getAnsiFGColor(252);
        final String bg = getAnsiBGColor(236);
        System.out.println(bg + col2 + "   __  __                  _                          " + ANSI_RESET);
        System.out.println(bg + col2 + "  |  \\/  | ___  _ __ _ __ | |__   ___ _   _ ___       " + ANSI_RESET);
        System.out.println(bg + col3 + "  | |\\/| |/ _ \\| '__| '_ \\| '_ \\ / _ \\ | | / __|      " + ANSI_RESET);
        System.out.println(bg + col3 + "  | |  | | (_) | |  | |_) | | | |  __/ |_| \\__ \\      " + ANSI_RESET);
        System.out.println(bg + col2 + "  |_|  |_|\\___/|_|  | .__/|_| |_|\\___|\\__,_|___/      " + ANSI_RESET);
        System.out.println(bg + col2 + "                    |_|                               " + ANSI_RESET);
        System.out.println(col1 + "  Version: " + Version.VERSION + ANSI_RESET);
        pr("[rd]Usage:[r] [bld]Morpheus[r] [b]<commandName>[r] " + col2 + "[--theme=themename] [--morphiumcfg=connetionName] [arg1=value1 arg2=value2 ...][r]");
        pr("    themes and connections are configured in morpheusconfig file (usually [ul]~/.config/morpheus.properties[r])");
        pr("    to get a list of themes, use " + col2 + " --theme=?[r]");
        pr("    to get a list of configs and choose, use " + col2 + " --morphiumcfg=?[r]");
    }

    private final Map<String, String> ansiCodes = new HashMap<>();

    private Morphium morphium;

    private Messaging messaging;



    private String theme;

    private String connection;

    private Properties properties;

    public Set<Class<? extends ICommand>> getCommandClasses() {
        final Set<Class<? extends ICommand>> ret = new HashSet<>();

        for (final Class<? extends ICommand> c : commands.values()) {
            ret.add(c);
        }

        return ret;
    }

    public Morphium getMorphium() {
        return morphium;
    }

    public Messaging getMessaging() {
        return messaging;
    }

    public String getNameFromCommandClass(final Class<? extends ICommand> commandClass) throws NoSuchFieldException, IllegalAccessException {
        final Field nameField = commandClass.getDeclaredField("NAME");
        return (String) nameField.get(null);
    }

    public String getDocumentationFromCommandClass(final Class<? extends ICommand> commandClass) {
        try {
            final Field description = commandClass.getDeclaredField("DESCRIPTION");
            return (String) description.get(null);
        } catch (final Exception e) {
            return null;
        }
    }

    public String getAnsiFGColor(final int colNum) {
        final String col1 = "\u001B[38;5;" + colNum + "m";
        return col1;
    }

    public String getAnsiBGColor(final int colNum) {
        final String col1 = "\u001B[48;5;" + colNum + "m";
        return col1;
    }

    public String ansiReset() {
        return ANSI_RESET;
    }

    public String getAnsiString(final String str) {
        String out = str;

        for (final String k : ansiCodes.keySet()) {
            out = out.replaceAll("\\[" + k + "\\]", ansiCodes.get(k));
        }

        for (int i = 0; i < 255; i++) {
            out = out.replaceAll("\\[fg" + i + "\\]", getAnsiFGColor(i));
            out = out.replaceAll("\\[bg" + i + "\\]", getAnsiBGColor(i));
        }

        return out;
    }

    public void pr(final String str) {
        System.out.println(getAnsiString(str));
    }

    public String getColumn(String str, final int len) {
        if (str.length() >= len) {
            return str.substring(0, len);
        }

        final int ctr = (len - str.length()) / 2;

        for (int i = 0; i < ctr; i++) {
            str = " " + str + " ";
        }

        while (str.length() > len) {
            str = str.substring(0, str.length() - 1);
        }

        while (str.length() < len) {
            str = str + " ";
        }

        return str;
    }

    public void pr(final String str, final int themeGradientNr) {
        final String gradient = properties.getProperty("theme." + theme + ".gradient" + themeGradientNr, "grey");
        pr(str, Gradient.valueOf(gradient));
    }


    public void pr(final String str, final Gradient gr) {
        final int l = str.length();
        int gradient[];

        switch (gr) {
            case yellow:
                gradient = new int[] {184, 220, 226, 227, 228, 229, 230, 231};
                break;
            case cyan:
                gradient = new int[] {29, 41, 42, 43, 48, 49, 50, 51};
                break;
            case blue:
                gradient = new int[] {25, 26, 27, 32, 33, 38, 39};
                break;
            case green:
                gradient = new int[] {22, 28, 34, 40, 46, 118, 119, 120};
                break;
            case red:
                gradient = new int[] {88, 124, 125, 160, 196};
                break;
            case purple:
                gradient = new int[] {53, 91, 127, 163, 199};
                break;
            case grey:
            default:
                gradient = new int[] {241, 243, 245, 248, 249, 252, 254, 231};
        }

        int chk = l / (gradient.length * 2);

        if (chk == 0) {
            chk = 1;
        }

        int gridx = 0;
        boolean flag = true;
        int off = 1;

        for (int idx = 0; idx < l; idx++) {
            if (flag) {
                System.out.print(getAnsiFGColor(gradient[gridx]));
                gridx += off;

                if (gridx >= gradient.length - 1) {
                    off = -off;
                    gridx = gradient.length - 1;
                } else if (gridx <= 0) {
                    gridx = 0;
                }

                flag = false;
            }

            if (idx != 0 && idx % chk == 0) {
                flag = true;
            }

            System.out.print(str.charAt(idx));
        }

        System.out.println(ANSI_RESET);
    }

    private void runApp(final String args[]) throws Exception {
        //disabling logback
        // LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        // JoranConfigurator jc = new JoranConfigurator();
        // jc.setContext(context);
        // context.reset(); // override default configuration
        //color codes...
        properties = new Properties();
        final String userHomeDir = System.getProperty("user.home");
        final var f = new File(userHomeDir + "/.config/morpheus.properties");

        if (!f.exists()) {
            // properties.setProperty("theme.default.bg","");
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
            final MorphiumConfig cfg = new MorphiumConfig();
            cfg.addHostToSeed("localhost", 27017);
            cfg.setDatabase("test");
            cfg.setMongoAuthDb("admin");
            cfg.setMongoLogin("test");
            cfg.setMongoPassword("test");
            properties.putAll(cfg.asProperties("morphium.default_connection"));
            properties.put("morphium.default_connection.messaging.processMultiple", "true");
            properties.put("morphium.default_connection.messaging.multithreadded", "true");
            properties.put("morphium.default_connection.messaging.windowSize", "10");
            properties.put("morphium.default_connection.messaging.pause", "100");
            properties.put("morphium.default_connection.messaging.queueName", "msg");
            properties.put("morphium.default_connection.messaging.senderId", UUID.randomUUID().toString());
            final StringBuilder doc = new StringBuilder();
            doc.append("Default configuration for morpheus\n");
            doc.append("Define morphium connection / settings with prefixes morphium.CONNECTIONNAME\nyou can then refer to it via commandline");
            doc.append("Theme definition:\n");
            doc.append("define the color-settings mentioned below, settings refer to keys");
            properties.store(new FileWriter(f), doc.toString());
        } else {
            properties.load(new FileReader(f));
        }

        // moveCursor(1, 25);
        pr("  Terminal Size: [good]" + getTerminalSize().toString());
        final String commandName = args[0];

        if (commandName.startsWith("-")) {
            pr(" [rd]Commands do not start with - -- maybe try 'list'?[r]");
            System.exit(1);
        }

        pr("  CommandName: '" + commandName + "'");
        final Map<String, String> commandArgs = parseCommandArgs(args);
        theme = "default";

        if (commandArgs.containsKey("--theme")) {
            //choose theme
            if (commandArgs.get("--theme").equals("?") || commandArgs.get("--theme").equals("list")) {
                //show list of themes
                final Set<String> themes = new HashSet<>();

                for (final Object k : properties.keySet()) {
                    if (k.toString().startsWith("theme")) {
                        final String t = k.toString().split("\\.")[1];
                        themes.add(t);
                    }
                }

                pr("=========== Configured themes: ===========", Gradient.green);

                for (final String t : themes) {
                    System.out.println("Theme: " + t);
                }

                System.exit(0);
            } else {
                theme = commandArgs.get("--theme");
            }
        }

        //adding theme settings
        for (final Object k : properties.keySet()) {
            if (k.toString().startsWith("theme." + theme)) {
                final String themeKey = k.toString().split("\\.")[2]; //should be the key
                ansiCodes.put(themeKey, getAnsiString(properties.getProperty(k.toString())));
            }
        }

        connection = "default_connection";

        if (commandArgs.containsKey("--morphiumcfg")) {
            if (commandArgs.get("--morphiumcfg").equals("?") || commandArgs.get("--morphiumcfg").equals("list")) {
                final Set<String> cfg = new HashSet<>();

                for (final Object k : properties.keySet()) {
                    if (k.toString().startsWith("morphium.")) {
                        final String t = k.toString().split("\\.")[1];
                        cfg.add(t);
                    }
                }

                pr("=========== Configured connections: ===========", Gradient.green);
                int i = 1;
                final Map<String, String> map = new HashMap<>();

                for (final String t : cfg) {
                    pr("[good]" + i + "[r]. Connection name: [header1]" + t + "[r]");
                    map.put("" + i, t);
                    i++;
                }

                pr("[c1]Choose connection or x to quit....[r]");
                final int input = System.in.read();
                final char c = (char) input;

                if (!map.containsKey("" + c)) {
                    pr("[c2] aborting[r]");
                    System.exit(0);
                }

                pr("[good]you chose: " + c + "[c2]" + map.get("" + c));
                connection = map.get("" + c);
            } else {
                connection = commandArgs.get("--morphiumcfg");
            }
        }

        pr("Connecting to mongo [good] " + connection + "[r] ");
        final MorphiumConfig cfg = MorphiumConfig.fromProperties("morphium." + connection, properties);
        pr("[error] IndexCheck: " + cfg.getIndexCheck().name());

        if (cfg.getHostSeed() == null || cfg.getHostSeed().isEmpty()) {
            pr("[error]--> connection not configured properly -no hosts set[r]");
            System.exit(1);
        }

        cfg.setMinConnections(2);
        cfg.setMaxConnections(8);
        cfg.setHousekeepingTimeout(1000);
        cfg.setDefaultReadPreference(ReadPreference.secondaryPreferred());
        cfg.setMaxWaitTime(10000);
        cfg.setIndexCheck(IndexCheck.NO_CHECK);
        cfg.setCappedCheck(CappedCheck.NO_CHECK);
        morphium = new Morphium(cfg);
        final boolean processMultiple = properties.getProperty("morphium." + connection + ".messaging.processMultiple", "true").equals("true");
        final boolean multithreadded = properties.getProperty("morphium." + connection + ".messaging.multiThreadded", "true").equals("true");
        final int pause = Integer.valueOf(properties.getProperty("morphium." + connection + ".messaging.pause", "100"));
        final int windowSize = Integer.valueOf(properties.getProperty("morphium." + connection + ".messaging.windowSize", "10"));
        final String queueName = properties.getProperty("morphium." + connection + ".messaging.queueName", "msg");
        final String senderId = properties.getProperty("morphium." + connection + ".messaging.senderId", UUID.randomUUID().toString());
        final var pd = ((PooledDriver) morphium.getDriver());
        Thread.sleep(2000);

        while (!morphium.getDriver().isConnected()) {
            pr("[warning]not connected yet...[r]" + pd.getMaxConnections() + "/" + pd.getMaxConnectionsPerHost());
            // var con = pd.getPrimaryConnection(null);
            // var cons = new ArrayList<MongoConnection>();
            //
            // for (int i = 0; i < 2; i++) {
            //     cons.add(pd.getReadConnection(null));
            // }
            // var con=morphium.getDriver().getPrimaryConnection(null);
            // pr("got connection?!?!? minConnections: "+morphium.getDriver().getMinConnectionsPerHost());
            // morphium.getDriver().releaseConnection(con);
            Thread.sleep(1000);
            // pd.releaseConnection(con);
            //
            // for (MongoConnection con2 : cons) {
            //     pd.releaseConnection(con2);
            // }
        }

        pr("[good]connection established[r]: Hosts: [c1]" + morphium.getDriver().getHostSeed().get(0) + "[r]");
        final var con = pd.getReadConnection(null);
        pd.releaseConnection(con);
        final var cons = pd.getNumConnectionsByHost();

        for (final var k : cons.keySet()) {
            pr("Connections to " + k + ": " + cons.get(k));
        }

        messaging = new Messaging(morphium, pause, processMultiple, multithreadded, windowSize);

        if (!queueName.equals("msg")) {
            messaging.setQueueName(queueName);
        }

        messaging.setSenderId(senderId);
        pr("[c1]Messaging configured - starting it[r] ");
        messaging.start();

        try {
            final Set<Class<? extends ICommand>> commandClasses = getCommandClasses();

            for (final Class<? extends ICommand> commandClass : commandClasses) {
                final String name = getNameFromCommandClass(commandClass);

                if (name.equals(commandName)) {
                    final ICommand command = commandClass.getDeclaredConstructor().newInstance();
                    command.execute(this, commandArgs);
                    return;
                    // } else {
                    //     pr(name+"!="+commandName);
                }
            }

            throw new ClassNotFoundException("name " + commandName + " unknown");
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            System.err.println("Invalid command provided." + e.getClass().getName() + "/" + e.getMessage());
            printUsage();
        } finally {
            messaging.terminate();
            morphium.close();
        }
    }

    private void registerAnsiCodes() {
        commands.put(HelloCommand.NAME, HelloCommand.class);
        commands.put(ListCommands.NAME, ListCommands.class);
        commands.put(GetStatus.NAME, GetStatus.class);
        commands.put(MessageMonitor.NAME, MessageMonitor.class);
        commands.put(SendMessageCommand.NAME, SendMessageCommand.class);
        ansiCodes.put("r", ANSI_RESET);
        ansiCodes.put("rd", ANSI_RED);
        ansiCodes.put("gr", ANSI_GREEN);
        ansiCodes.put("y", ANSI_YELLOW);
        ansiCodes.put("b", ANSI_BLUE);
        ansiCodes.put("bl", ANSI_BLACK);
        ansiCodes.put("m", ANSI_MAGENTA);
        ansiCodes.put("c", ANSI_CYAN);
        ansiCodes.put("w", ANSI_WHITE);
        ansiCodes.put("b_bl", ANSI_BG_BLACK);
        ansiCodes.put("b_rd", ANSI_BG_RED);
        ansiCodes.put("b_gr", ANSI_BG_GREEN);
        ansiCodes.put("b_y", ANSI_BG_YELLOW);
        ansiCodes.put("b_b", ANSI_BG_BLUE);
        ansiCodes.put("b_m", ANSI_BG_MAGENTA);
        ansiCodes.put("b_c", ANSI_BG_CYAN);
        ansiCodes.put("b_w", ANSI_BG_WHITE);
        ansiCodes.put("ital", ANSI_ITALIC);
        ansiCodes.put("bld", ANSI_BOLD);
        ansiCodes.put("fnt", ANSI_FAINT);
        ansiCodes.put("sb", ANSI_SLOW_BLINK);
        ansiCodes.put("fb", ANSI_FAST_BLINK);
        ansiCodes.put("inv", ANSI_INVERT);
        ansiCodes.put("hid", ANSI_HIDDEN);
        ansiCodes.put("str", ANSI_STRIKETHROUGH);
        ansiCodes.put("ul", ANSI_UNDERLINE);
    }

    private Map<String, String> parseCommandArgs(final String[] args) {
        final Map<String, String> commandArgs = new HashMap<>();

        for (int i = 1; i < args.length; i++) {
            final String[] splitArg = args[i].split("=");
            commandArgs.put(splitArg[0], splitArg[1]);
        }

        return commandArgs;
    }

}

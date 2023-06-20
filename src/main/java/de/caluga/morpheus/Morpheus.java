package de.caluga.morpheus;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import de.caluga.morphium.MorphiumConfig.CappedCheck;
import de.caluga.morphium.MorphiumConfig.IndexCheck;
import de.caluga.morphium.driver.ReadPreference;
import de.caluga.morphium.driver.wire.MongoConnection;
import de.caluga.morphium.driver.wire.PooledDriver;
import de.caluga.morphium.messaging.Messaging;

public class Morpheus {
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

    private Map<String, String> ansiCodes = new HashMap<>();
    private Morphium morphium;
    private Messaging messaging;
    private String theme;
    private String connection;
    private Properties properties;

    public static void main(String args[]) throws Exception {
        if (args.length < 1) {
            printUsage();
            return;
        }

        var app = new Morpheus();
        app.runApp(args);
    }

    private void runApp(String args[]) throws Exception {
        //disabling logback
        // LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        // JoranConfigurator jc = new JoranConfigurator();
        // jc.setContext(context);
        // context.reset(); // override default configuration
        //color codes...
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
        String col1 = getAnsiFGColor(241);
        String col2 = getAnsiFGColor(245);
        String col3 = getAnsiFGColor(252);
        String bg = getAnsiBGColor(236);
        System.out.println(bg + col1 + "   __  __                  _                          " + ANSI_RESET);
        System.out.println(bg + col2 + "  |  \\/  | ___  _ __ _ __ | |__   ___ _   _ ___       " + ANSI_RESET);
        System.out.println(bg + col3 + "  | |\\/| |/ _ \\| '__| '_ \\| '_ \\ / _ \\ | | / __|      " + ANSI_RESET);
        System.out.println(bg + col3 + "  | |  | | (_) | |  | |_) | | | |  __/ |_| \\__ \\      " + ANSI_RESET);
        System.out.println(bg + col2 + "  |_|  |_|\\___/|_|  | .__/|_| |_|\\___|\\__,_|___/      " + ANSI_RESET);
        System.out.println(bg + col1 + "                    |_|                               " + ANSI_RESET);
        System.out.println(col1 + "  Version: " + Version.VERSION + ANSI_RESET);
        properties = new Properties();
        var f = new File("/Users/stephan/.config/morpheus.properties");

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
            MorphiumConfig cfg = new MorphiumConfig();
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
            StringBuilder doc = new StringBuilder();
            doc.append("Default configuration for morpheus\n");
            doc.append("Define morphium connection / settings with prefixes morphium.CONNECTIONNAME\nyou can then refer to it via commandline");
            doc.append("Theme definition:\n");
            doc.append("define the color-settings mentioned below, settings refer to keys");
            properties.store(new FileWriter(f), doc.toString());
        } else {
            properties.load(new FileReader(f));
        }
        moveCursor(1, 25);
        pr("  Terminal Size: [good]" + getTerminalSize().toString());
        String commandName = args[0];
        Map<String, String> commandArgs = parseCommandArgs(args);
        theme = "default";

        if (commandArgs.containsKey("--theme")) {
            //choose theme
            if (commandArgs.get("--theme").equals("?")) {
                //show list of themes
                Set<String> themes = new HashSet<>();

                for (Object k : properties.keySet()) {
                    if (k.toString().startsWith("theme")) {
                        String t = k.toString().split("\\.")[1];
                        themes.add(t);
                    }
                }

                pr("=========== Configured themes: ===========", Gradient.green);

                for (String t : themes) {
                    System.out.println("Theme: " + t);
                }

                System.exit(0);
            } else {
                theme = commandArgs.get("--theme");
            }
        }

        //adding theme settings
        for (Object k : properties.keySet()) {
            if (k.toString().startsWith("theme." + theme)) {
                String themeKey = k.toString().split("\\.")[2]; //should be the key
                ansiCodes.put(themeKey, getAnsiString(properties.getProperty(k.toString())));
            }
        }

        connection = "default_connection";

        if (commandArgs.containsKey("--morphiumcfg")) {
            if (commandArgs.get("--morphiumcfg").equals("?")) {
                Set<String> cfg = new HashSet<>();

                for (Object k : properties.keySet()) {
                    if (k.toString().startsWith("morphium.")) {
                        String t = k.toString().split("\\.")[1];
                        cfg.add(t);
                    }
                }

                pr("=========== Configured connections: ===========", Gradient.green);
                int i = 1;
                Map<String, String> map = new HashMap<>();

                for (String t : cfg) {
                    pr("[good]" + i + "[r]. Connection name: [header1]" + t + "[r]");
                    map.put("" + i, t);
                    i++;
                }

                pr("[c1]Choose connection or x to quit....[r]");
                int input = System.in.read();
                char c = (char) input;

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
        MorphiumConfig cfg = MorphiumConfig.fromProperties("morphium." + connection, properties);

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
        boolean processMultiple = properties.getProperty("morphium." + connection + ".messaging.processMultiple", "true").equals("true");
        boolean multithreadded = properties.getProperty("morphium." + connection + ".messaging.multiThreadded", "true").equals("true");
        int pause = Integer.valueOf(properties.getProperty("morphium." + connection + ".messaging.pause", "100"));
        int windowSize = Integer.valueOf(properties.getProperty("morphium." + connection + ".messaging.windowSize", "10"));
        String queueName = properties.getProperty("morphium." + connection + ".messaging.queueName", "msg");
        String senderId = properties.getProperty("morphium." + connection + ".messaging.senderId", UUID.randomUUID().toString());
        var pd = ((PooledDriver) morphium.getDriver());
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
        var con = pd.getReadConnection(null);
        pd.releaseConnection(con);
        var cons = pd.getNumConnectionsByHost();

        for (var k : cons.keySet()) {
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
            Set<Class<? extends ICommand>> commandClasses = getCommandClasses();

            for (Class<? extends ICommand> commandClass : commandClasses) {
                String name = getNameFromCommandClass(commandClass);

                if (name.equals(commandName)) {
                    ICommand command = commandClass.getDeclaredConstructor().newInstance();
                    command.execute(this, commandArgs);
                    return;
                }
            }

            throw new ClassNotFoundException("name unknown");
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            System.err.println("Invalid command provided." + e.getClass().getName() + "/" + e.getMessage());
            printUsage();
        } finally {
            messaging.terminate();
            morphium.close();
        }
    }

    private Map<String, String> parseCommandArgs(String[] args) {
        Map<String, String> commandArgs = new HashMap<>();

        for (int i = 1; i < args.length; i++) {
            String[] splitArg = args[i].split("=");
            commandArgs.put(splitArg[0], splitArg[1]);
        }

        return commandArgs;
    }

    public Morphium getMorphium() {
        return morphium;
    }

    public Messaging getMessaging() {
        return messaging;
    }

    public String getNameFromCommandClass(Class<? extends ICommand> commandClass) throws NoSuchFieldException, IllegalAccessException {
        Field nameField = commandClass.getDeclaredField("NAME");
        return (String) nameField.get(null);
    }

    public Set<Class<? extends ICommand>> getCommandClasses() throws IOException, ClassNotFoundException {
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        Set<Class<? extends ICommand>> classes = new HashSet<>();
        String packagePath = COMMANDS_PACKAGE.replace('.', '/');
        Enumeration<URL> resources = classloader.getResources(packagePath);

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            File file = new File(resource.getFile());

            if (file.isDirectory()) {
                String[] classNames = file.list();

                for (String className : classNames) {
                    className = COMMANDS_PACKAGE + className.substring(0, className.lastIndexOf('.'));
                    Class<?> cls = Class.forName(className);

                    if (ICommand.class.isAssignableFrom(cls)) {
                        classes.add((Class<? extends ICommand>) cls);
                    }
                }
            }
        }

        return classes;
    }

    public String getDocumentationFromCommandClass(Class<? extends ICommand> commandClass) {
        try {
            Field description = commandClass.getDeclaredField("DESCRIPTION");
            return (String) description.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static void printUsage() {
        System.out.println("Usage: Morpheus [--theme=themename] [--morphiumcfg=connetionName] <commandName> [arg1=value1 arg2=value2 ...]");
        System.out.println("    themes and connections are configured in morpheusconfig file (usually ~/.config/morpheus.properties");
    }

    public String getAnsiFGColor(int colNum) {
        String col1 = "\u001B[38;5;" + colNum + "m";
        return col1;
    }

    public String getAnsiBGColor(int colNum) {
        String col1 = "\u001B[48;5;" + colNum + "m";
        return col1;
    }

    public String ansiReset() {
        return ANSI_RESET;
    }

    public String getAnsiString(String str) {
        String out = str;

        for (String k : ansiCodes.keySet()) {
            out = out.replaceAll("\\[" + k + "\\]", ansiCodes.get(k));
        }

        for (int i = 0; i < 255; i++) {
            out = out.replaceAll("\\[fg" + i + "\\]", getAnsiFGColor(i));
            out = out.replaceAll("\\[bg" + i + "\\]", getAnsiBGColor(i));
        }

        return out;
    }

    public void pr(String str) {
        System.out.println(getAnsiString(str));
    }

    public String getColumn(String str, int len) {
        if (str.length() >= len) {
            return str.substring(0, len);
        }

        int ctr = (len - str.length()) / 2;

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

    public void pr(String str, int themeGradientNr) {
        String gradient = properties.getProperty("theme." + theme + ".gradient" + themeGradientNr, "grey");
        pr(str, Gradient.valueOf(gradient));
    }

    public void pr(String str, Gradient gr) {
        int l = str.length();
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

    public static Size getTerminalSize() {
        try {
            String[] cmd = {"/bin/sh", "-c", "tput cols && tput lines"};
            Process process = Runtime.getRuntime().exec(cmd);
            process.waitFor();
            // Read the terminal response
            byte[] input = new byte[32];
            int size = process.getInputStream().read(input);
            String[] output = new String(input, 0, size).split("\\s+");
            int cols = Integer.parseInt(output[0]);
            int rows = Integer.parseInt(output[1]);
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
        } catch (Exception e) {
            e.printStackTrace();

        }

        return null;
    }


    public static void moveCursor(int row,int col){
        System.out.print("\u001B["+row+";"+col+"H");
    }

    public static class Size {
        private int col, row;

        public Size(int col, int row) {
            this.col = col;
            this.row = row;
        }

        public int getCol() {
            return col;
        }

        public void setCol(int col) {
            this.col = col;
        }

        public int getRow() {
            return row;
        }

        public void setRow(int row) {
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

}

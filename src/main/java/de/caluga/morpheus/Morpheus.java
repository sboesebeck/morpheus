package de.caluga.morpheus;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;

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

    public static void main(String args[]) throws Exception {
        if (args.length < 1) {
            printUsage();
            return;
        }

        var app = new Morpheus();
        app.runApp(args);
    }


    private void runApp(String args[]) throws Exception {
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
        System.out.println(col1+"  Version: "+Version.VERSION+ANSI_RESET);

        Properties p=new Properties();
        var f=new File("/Users/stephan/.config/morpheus.properties");

        if (!f.exists()){
            p.setProperty("theme.default.c1", "[rd]");
            p.setProperty("theme.default.c2", "[bg1][bld][fg230]");
            p.setProperty("theme.default.c3","[gr]");
            p.setProperty("theme.default.error","[rd]");
            p.setProperty("theme.default.gradient1","grey");
            p.setProperty("theme.default.gradient1","green");
            MorphiumConfig cfg=new MorphiumConfig();
            cfg.addHostToSeed("localhost", 27017);
            cfg.setDatabase("test");
            p.putAll(cfg.asProperties("morphium.default_connection"));
            StringBuilder doc=new StringBuilder();
            doc.append("Default configuration for morpheus\n");
            doc.append("Define morphium connection / settings with prefixes morphium.CONNECTIONNAME\nyou can then refer to it via commandline");
            doc.append("Theme definition:\n");
            doc.append("define the color-settings mentioned below, settings refer to keys");
            p.store(new FileWriter(f),doc.toString());
        } else {
            p.load(new FileReader(f));
        }

        String commandName = args[0];
        Map<String, String> commandArgs = parseCommandArgs(args);
        String theme="default";
        if (commandArgs.containsKey("--theme")){
            //choose theme
            if (commandArgs.get("--theme").equals("?")){
                //show list of themes
                Set<String> themes=new HashSet<>();
                for (Object k:p.keySet()){
                    if (k.toString().startsWith("theme")){
                        String t=k.toString().split("\\.")[1];
                        themes.add(t);
                    }
                }
                pr("=========== Configured themes: ===========",Gradient.green);
                for (String t:themes){
                   System.out.println("Theme: "+t);
                }
                System.exit(0);

            } else {
                theme=commandArgs.get("--theme");
            }
        }
        String connection="default_connection";
        if (commandArgs.containsKey("--morphiumcfg")){
            if (commandArgs.get("--morphiumcfg").equals("?")){
                Set<String> cfg=new HashSet<>();
                for (Object k:p.keySet()){
                    if (k.toString().startsWith("morphium.")){
                        String t=k.toString().split("\\.")[1];
                        cfg.add(t);
                    }
                }
                pr("=========== Configured connections: ===========",Gradient.green);
                for (String t:cfg){
                   System.out.println("Connection name: "+t);
                }
                System.exit(0);
            } else {
                connection=commandArgs.get("--morphiumcfg");
            }
        }

        pr("[c1]Connecting to mongo [c2] "+connection+"[r] ");
        MorphiumConfig cfg=MorphiumConfig.fromProperties("morphium."+connection, p);
        morphium=new Morphium(cfg);

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

    public Morphium getMorphium(){
        return morphium;
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
        System.out.println("Usage: CommandLineParser <commandName> [arg1=value1 arg2=value2 ...]");
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

        String out=str;

        for (String k:ansiCodes.keySet()){
            out=out.replaceAll("\\["+k+"\\]",ansiCodes.get(k));
        }
        for (int i=0;i<255;i++){
            out=out.replaceAll("\\[fg"+i+"\\]",getAnsiFGColor(i));
            out=out.replaceAll("\\[bg"+i+"\\]",getAnsiBGColor(i));
        }
        return out;
    }


    public void pr(String str) {
        System.out.println(getAnsiString(str));
    }

    public void pr(String str, Gradient gr) {
        int l = str.length();
        int gradient[];

        switch (gr) {
            case yellow:
                gradient = new int[] { 184,220,226,227,228,229,230,231};
                break;
            case blue:
                gradient = new int[] { 21, 69, 67,12, 153, 117,159};
                break;
            case green:
                gradient = new int[] {22, 28, 34, 40, 46, 118, 119, 120};
                break;
            case red:
                gradient = new int[] {89, 124, 160, 198};
                break;
            case grey:
            default:
                gradient = new int[] {241, 243, 245, 248, 249, 252, 254};
        }

        int chk = l / (gradient.length * 2);
        if (chk==0)chk=1;
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



    public enum Gradient {
        blue, grey, green, red,yellow,
    }

}

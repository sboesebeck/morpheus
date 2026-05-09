package de.caluga.morpheus;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Morpheus {
    public final static String COMMANDS_PACKAGE = "de.caluga.morpheus.commands.";
    String ANSI_RESET = "\u001B[0m";
    String ANSI_BLACK="\u001B[30m";
    String ANSI_RED = "\u001B[31m";
    String ANSI_GREEN = "\u001B[32m";
    String ANSI_BLUE = "\u001B[34m";
    String ANSI_YELLOW="\u001B[33m";
    String ANSI_MAGENTA="\u001B[35m";
    String ANSI_CYAN="\u001B[36m";
    String ANSI_WHITE="\u001B[37m";


    String ANSI_BG_BLACK="\u001B[40m";
    String ANSI_BG_RED = "\u001B[41m";
    String ANSI_BG_GREEN = "\u001B[42m";
    String ANSI_BG_BLUE = "\u001B[44m";
    String ANSI_BG_YELLOW="\u001B[43m";
    String ANSI_BG_MAGENTA="\u001B[45m";
    String ANSI_BG_CYAN="\u001B[46m";
    String ANSI_BG_WHITE="\u001B[47m";

    String ANSI_BOLD = "\u001B[1m";
    String ANSI_FAINT= "\u001B[2m";
    String ANSI_ITALIC= "\u001B[3m";
    String ANSI_UNDERLINE = "\u001B[4m";
    String ANSI_SLOW_BLINK= "\u001B[5m";
    String ANSI_FAST_BLINK= "\u001B[6m";
    String ANSI_INVERT= "\u001B[7m";
    String ANSI_HIDDEN= "\u001B[8m";
    String ANSI_STRIKETHROUGH= "\u001B[9m";


    public static void main(String args[]) throws Exception {
        if (args.length < 1) {
            printUsage();
            return;
        }
        var app=new Morpheus();
        app.runApp(args);

    }


    private void runApp(String args[]) throws Exception {
        String col1=getAnsiFGColor(67);
        String col2=getAnsiFGColor(33);
        String col3=getAnsiFGColor(75);
        String bg=""; //getAnsiBGColor(253);
        System.out.println(bg+col1+" __  __                  _                          "+ANSI_RESET);
        System.out.println(bg+col2+"|  \\/  | ___  _ __ _ __ | |__   ___ _   _ ___      "+ANSI_RESET);
        System.out.println(bg+col3+"| |\\/| |/ _ \\| '__| '_ \\| '_ \\ / _ \\ | | / __| "+ANSI_RESET);
        System.out.println(bg+col3+"| |  | | (_) | |  | |_) | | | |  __/ |_| \\__ \\    "+ANSI_RESET);
        System.out.println(bg+col2+"|_|  |_|\\___/|_|  | .__/|_| |_|\\___|\\__,_|___/   "+ANSI_RESET);
        System.out.println(bg+col1+"                  |_|                               "+ANSI_RESET);


        String commandName = args[0];
        Map<String, String> commandArgs = parseCommandArgs(args);

        try {
            Set<Class<? extends ICommand>> commandClasses = getCommandClasses();

            for (Class<? extends ICommand> commandClass : commandClasses) {
                String name = getNameFromCommandClass(commandClass);

                if (name.equals(commandName)) {
                    ICommand command = commandClass.getDeclaredConstructor().newInstance();
                    command.execute(this,commandArgs);
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



    public String getAnsiFGColor(int colNum){
        String col1="\u001B[38;5;"+colNum+"m";
        return col1;
    }


    public String getAnsiBGColor(int colNum){
        String col1="\u001B[48;5;"+colNum+"m";
        return col1;
    }


    public String getAnsiString(String str){
        return str.replaceAll("<b>",ANSI_BOLD)
            .replaceAll("<c1>", ANSI_RED)
            .replaceAll("<c2>",ANSI_CYAN)
            .replaceAll("<c3>",ANSI_GREEN);
    }


}

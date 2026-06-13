package de.caluga.morpheus.cli;

import picocli.CommandLine;

/** CLI entry point: builds the command tree, runs it, cleans up. */
public class MorpheusCli {

    public static CommandLine buildCommandLine(RootCommand root) {
        CommandLine cl = new CommandLine(root);
        cl.addSubcommand("messages", new MessagesCommand());
        cl.addSubcommand("topics", new TopicsCommand());
        cl.addSubcommand("nodes", new NodesCommand());
        cl.addSubcommand("status", new StatusCommand());
        cl.addSubcommand("send", new SendCommand());
        cl.addSubcommand("watch", new WatchCommand());
        cl.addSubcommand("config", new ConfigCommands());
        return cl;
    }

    public static int run(String[] args) {
        RootCommand root = new RootCommand();
        try {
            return buildCommandLine(root).execute(args);
        } finally {
            root.closeContext();
        }
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }
}

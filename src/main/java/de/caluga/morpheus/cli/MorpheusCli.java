package de.caluga.morpheus.cli;

import picocli.CommandLine;

/** CLI entry point: builds the command tree, runs it, cleans up. */
public class MorpheusCli {

    public static CommandLine buildCommandLine(RootCommand root) {
        CommandLine cl = new CommandLine(root);
        // subcommands are registered here as they are ported (Tasks 7-10)
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

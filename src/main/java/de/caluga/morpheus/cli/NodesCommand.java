package de.caluga.morpheus.cli;

import picocli.CommandLine.Command;

@Command(name = "nodes", description = "Sender → answerer pair activity (TUI).",
         mixinStandardHelpOptions = true)
public class NodesCommand extends AbstractViewCommand {
    @Override
    protected String viewName() { return "nodes"; }
}

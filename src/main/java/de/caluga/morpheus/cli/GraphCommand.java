package de.caluga.morpheus.cli;

import picocli.CommandLine.Command;

@Command(name = "graph", description = "Live message-flow graph (TUI).",
         mixinStandardHelpOptions = true)
public class GraphCommand extends AbstractViewCommand {
    @Override
    protected String viewName() { return "graph"; }
}

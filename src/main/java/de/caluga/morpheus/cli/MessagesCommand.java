package de.caluga.morpheus.cli;

import picocli.CommandLine.Command;

@Command(name = "messages", description = "Live messaging monitor (TUI).",
         mixinStandardHelpOptions = true)
public class MessagesCommand extends AbstractViewCommand {
    @Override
    protected String viewName() { return "messages"; }
}

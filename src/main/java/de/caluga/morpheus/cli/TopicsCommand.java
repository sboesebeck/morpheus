package de.caluga.morpheus.cli;

import picocli.CommandLine.Command;

@Command(name = "topics", description = "Per-topic message/answer aggregates (TUI).",
         mixinStandardHelpOptions = true)
public class TopicsCommand extends AbstractViewCommand {
    @Override
    protected String viewName() { return "topics"; }
}

package de.caluga.morpheus.cli;

import picocli.CommandLine.Command;

@Command(name = "status", description = "Live node roster / status pings (TUI).",
         mixinStandardHelpOptions = true)
public class StatusViewCommand extends AbstractViewCommand {
    @Override
    protected String viewName() { return "status"; }
}

package de.caluga.morpheus.cli;

import de.caluga.morpheus.tui.MorpheusTui;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/** Base for commands that open the TUI directly on one view (launcher skipped, Esc/q exits). */
public abstract class AbstractViewCommand implements Callable<Integer> {

    @ParentCommand
    RootCommand parent;

    /** The TUI view name this command opens (messages/topics/nodes/status). */
    protected abstract String viewName();

    @Override
    public Integer call() {
        // parent.context() applies all global options (-c/--messaging/--theme/-v) and does NOT connect.
        // ConnectingScreen connects async and the view takes ownership; do not close here.
        MorpheusTui.launchView(parent.context(), viewName());
        return 0;
    }
}

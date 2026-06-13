package de.caluga.morpheus.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

public class CliCommandSurfaceTest {

    private CommandLine cli() {
        return MorpheusCli.buildCommandLine(new RootCommand());
    }

    @Test
    void viewOpenersRegisteredAndMonitorGone() {
        var subs = cli().getSubcommands();
        assertTrue(subs.containsKey("messages"), "messages must be registered");
        assertTrue(subs.containsKey("topics"), "topics must be registered");
        assertTrue(subs.containsKey("nodes"), "nodes must be registered");
        assertFalse(subs.containsKey("monitor"), "monitor must be gone (renamed to messages)");
    }

    @Test
    void viewOpenersExposeCorrectViewNames() {
        assertEquals("messages", new MessagesCommand().viewName());
        assertEquals("topics", new TopicsCommand().viewName());
        assertEquals("nodes", new NodesCommand().viewName());
    }

    @Test
    void statusIsViewOpenerAndPingIsScriptable() {
        var subs = cli().getSubcommands();
        assertTrue(subs.containsKey("ping"), "ping must be registered");
        assertTrue(subs.get("status").getCommand() instanceof StatusViewCommand,
                "status must be the TUI roster opener");
        assertTrue(subs.get("ping").getCommand() instanceof PingCommand,
                "ping must be the scriptable command");
    }

    @Test
    void pingKeepsGraphiteOption() {
        var ping = cli().getSubcommands().get("ping");
        assertTrue(ping.getCommandSpec().optionsMap().containsKey("--graphite"),
                "ping must keep the --graphite option");
    }
}

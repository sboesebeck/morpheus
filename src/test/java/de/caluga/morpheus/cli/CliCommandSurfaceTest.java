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
    void pingHasNoGraphiteOption() {
        var ping = cli().getSubcommands().get("ping");
        assertFalse(ping.getCommandSpec().optionsMap().containsKey("--graphite"),
                "ping must not keep the --graphite option (graphite export was deleted)");
    }

    @Test
    void graphIsRegisteredViewOpener() {
        var subs = cli().getSubcommands();
        assertTrue(subs.containsKey("graph"));
        assertEquals("graph", new GraphCommand().viewName());
    }

    @Test
    void latencyAndPongRegistered() {
        var subs = cli().getSubcommands();
        assertTrue(subs.containsKey("latency"), "latency must be registered");
        assertTrue(subs.containsKey("pong"), "pong must be registered");
        assertTrue(subs.get("latency").getCommand() instanceof LatencyCommand);
        assertTrue(subs.get("pong").getCommand() instanceof PongCommand);
    }

    @Test
    void latencyHeadlessOptionsPresent() {
        var spec = cli().getSubcommands().get("latency").getCommandSpec();
        for (String opt : new String[]{"--headless", "--topic", "--status", "--interval", "--rate",
                "--ramp", "--threads", "--payload", "--timeout", "--duration",
                "--json", "--csv", "--graphite", "--max-loss", "--ramp-abort-loss", "--buffer",
                "--samples", "--exclusive"}) {
            assertTrue(spec.optionsMap().containsKey(opt), opt + " missing");
        }
        assertFalse(spec.optionsMap().containsKey("--graphite"), "--graphite must be deleted");
        assertFalse(spec.optionsMap().containsKey("--prefix"), "--prefix must be deleted (graphite-only)");
    }

    @Test
    void helpSubcommandRegistered() {
        var subs = cli().getSubcommands();
        assertTrue(subs.containsKey("help"), "help subcommand must be registered");
        var out = new java.io.StringWriter();
        var cl = cli();
        cl.setOut(new java.io.PrintWriter(out));
        assertEquals(0, cl.execute("help", "latency"));
        assertTrue(out.toString().contains("--headless"), "help latency must print the options");
    }

    @Test
    void pongPayloadOptionPresent() {
        var spec = cli().getSubcommands().get("pong").getCommandSpec();
        assertTrue(spec.optionsMap().containsKey("--payload"), "--payload missing on pong");
    }

    @Test
    void pongWorkOptionPresent() {
        var spec = cli().getSubcommands().get("pong").getCommandSpec();
        assertTrue(spec.optionsMap().containsKey("--work"), "--work missing on pong");
    }

    @Test
    void pingCommandUntouched() throws Exception {
        // guard: the latency feature must not have modified PingCommand
        var ping = cli().getSubcommands().get("ping");
        assertTrue(ping.getCommand() instanceof PingCommand);
        var opts = ping.getCommandSpec().optionsMap();
        for (String o : new String[]{"--wait", "--level", "--filter-host", "--filter-sender", "--keys"}) {
            assertTrue(opts.containsKey(o), o + " must be present on ping");
        }
    }

    @Test
    void uptimeRegisteredWithOptions() {
        var subs = cli().getSubcommands();
        assertTrue(subs.containsKey("uptime"), "uptime must be registered");
        assertTrue(subs.get("uptime").getCommand() instanceof UptimeCommand);
        var opts = subs.get("uptime").getCommandSpec().optionsMap();
        for (String o : new String[]{"--headless", "--duration", "--topic", "--interval", "--misses",
                "--timeout", "--no-rs-watch", "--json", "--events-csv", "--nodes-csv",
                "--samples-csv", "--fail-on-outage"}) {
            assertTrue(opts.containsKey(o), o + " must be present");
        }
    }
}

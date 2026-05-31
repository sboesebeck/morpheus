package de.caluga.morpheus.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

public class RootCommandTest {

    private CommandLine cli() {
        return MorpheusCli.buildCommandLine(new RootCommand());
    }

    @Test
    void helpFlagShowsUsageAndSucceeds() {
        // --help still prints usage; no-args now opens the TUI (covered by MorpheusTuiTest).
        StringWriter out = new StringWriter();
        CommandLine cl = cli();
        cl.setOut(new PrintWriter(out));
        int exit = cl.execute("--help");
        assertEquals(0, exit);
        assertTrue(out.toString().contains("Usage: morpheus"));
    }

    @Test
    void unknownOptionFailsWithUsageExitCode() {
        StringWriter err = new StringWriter();
        CommandLine cl = cli();
        cl.setErr(new PrintWriter(err));
        int exit = cl.execute("status", "--wiat", "5");
        assertEquals(2, exit, "usage errors must exit with 2");
        assertTrue(err.toString().contains("--wiat"));
    }

    @Test
    void helpListsSubcommands() {
        StringWriter out = new StringWriter();
        CommandLine cl = cli();
        cl.setOut(new PrintWriter(out));
        int exit = cl.execute("--help");
        assertEquals(0, exit);
        String help = out.toString();
        assertTrue(help.contains("status"));
        assertTrue(help.contains("send"));
        assertTrue(help.contains("watch"));
        assertTrue(help.contains("monitor"));
        assertTrue(help.contains("config"));
    }
}

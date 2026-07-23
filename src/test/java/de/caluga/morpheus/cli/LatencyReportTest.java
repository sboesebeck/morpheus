package de.caluga.morpheus.cli;

import de.caluga.morpheus.core.latency.LatencySample;
import de.caluga.morpheus.core.latency.LatencyStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LatencyReportTest {

    private LatencyStats.Snapshot snap() {
        LatencyStats st = new LatencyStats(100);
        st.recordPingSent(1);
        st.recordAnswer(new LatencySample(1, "pong-1", "hostA", 2.0, 1.5, 0.5, 1000));
        st.recordPingCompleted(1, true);
        return st.snapshot();
    }

    @Test
    void jsonContainsCountsAndResponder() {
        String json = LatencyCommand.reportJson(snap());
        assertTrue(json.contains("\"pingsSent\":1"));
        assertTrue(json.contains("\"key\":\"pong-1/hostA\""));
        assertTrue(json.contains("\"p99\":"));
    }

    @Test
    void csvHasHeaderAndRow() {
        String csv = LatencyCommand.reportCsv(snap());
        String[] lines = csv.strip().split("\n");
        assertEquals("key,count,lossPct,lastMs,minMs,avgMs,maxMs,p50,p90,p99,jitterMs,outboundMs,returnMs", lines[0]);
        assertTrue(lines[1].startsWith("pong-1/hostA,1,"));
    }

    @Test
    void csvNullPathSplitEmpty() {
        LatencyStats st = new LatencyStats(100);
        st.recordPingSent(1);
        st.recordAnswer(new LatencySample(1, "a", "h", 2.0, null, null, 0));
        String csv = LatencyCommand.reportCsv(st.snapshot());
        assertTrue(csv.strip().split("\n")[1].endsWith(",,"));
    }

    @Test
    void buildConfigRejectsRampWithWrongPartCount() {
        LatencyCommand cmd = new LatencyCommand();
        cmd.ramp = "50";
        cmd.status = false;
        var ex = assertThrows(picocli.CommandLine.ParameterException.class, () -> cmd.buildConfig(null));
        assertTrue(ex.getMessage().contains("start:step:stepSecs"),
                "expected the start:step:stepSecs format hint, got: " + ex.getMessage());
    }

    @Test
    void buildConfigRejectsNonNumericRamp() {
        LatencyCommand cmd = new LatencyCommand();
        cmd.ramp = "a:b:c";
        cmd.status = false;
        var ex = assertThrows(picocli.CommandLine.ParameterException.class, () -> cmd.buildConfig(null));
        assertTrue(ex.getMessage().contains("start:step:stepSecs"),
                "expected the start:step:stepSecs format hint, got: " + ex.getMessage());
    }

    @Test
    void buildConfigRejectsNonPositiveRampParts() {
        LatencyCommand cmd = new LatencyCommand();
        cmd.ramp = "0:10:5";
        cmd.status = false;
        var ex = assertThrows(picocli.CommandLine.ParameterException.class, () -> cmd.buildConfig(null));
        assertTrue(ex.getMessage().contains("start:step:stepSecs"),
                "expected the start:step:stepSecs format hint, got: " + ex.getMessage());
    }
}

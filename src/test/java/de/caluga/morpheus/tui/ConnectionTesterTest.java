package de.caluga.morpheus.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConnectionTesterTest {

    private void await(ConnectionTester t, String expectedPrefix, long maxMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + maxMs;
        while (System.currentTimeMillis() < deadline) {
            if (t.status().startsWith(expectedPrefix)) return;
            Thread.sleep(20);
        }
        fail("status never reached '" + expectedPrefix + "', was: " + t.status());
    }

    @Test
    void successPropagatesProbeResult() throws Exception {
        ConnectionTester t = new ConnectionTester(name -> "● 3 nodes, 12ms", 2000);
        t.start("prod");
        await(t, "●", 2000);
    }

    @Test
    void slowProbeTimesOut() throws Exception {
        ConnectionTester t = new ConnectionTester(name -> {
            Thread.sleep(5000);
            return "● should not see this";
        }, 200);
        t.start("prod");
        await(t, "✗ Timeout", 2000);
    }

    @Test
    void errorIsReported() throws Exception {
        ConnectionTester t = new ConnectionTester(name -> { throw new RuntimeException("boom"); }, 2000);
        t.start("prod");
        await(t, "✗", 2000);
    }

    @Test
    void idleBeforeStartIsEmpty() {
        ConnectionTester t = new ConnectionTester(name -> "x", 2000);
        assertEquals("", t.status());
    }
}

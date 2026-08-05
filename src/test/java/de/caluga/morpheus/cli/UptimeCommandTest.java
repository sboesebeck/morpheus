package de.caluga.morpheus.cli;

import de.caluga.morpheus.core.uptime.UptimeTracker;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class UptimeCommandTest {

    private UptimeTracker.Snapshot snap() {
        AtomicLong wall = new AtomicLong(0);
        UptimeTracker t = new UptimeTracker(1, wall::get);
        t.onPingComplete(1, 1000, Set.of("a/h1"));
        t.onPingComplete(2, 2000, Set.of());
        t.onPingComplete(3, 9000, Set.of("a/h1"));
        wall.set(10_000);
        return t.snapshot();
    }

    @Test
    void reportJsonContainsNodesAndTotals() {
        String json = UptimeCommand.reportJson(snap());
        assertTrue(json.contains("\"node\":\"a/h1\""), json);
        assertTrue(json.contains("\"state\":\"UP\""), json);
        assertTrue(json.contains("\"outages\":1"), json);
        assertTrue(json.contains("\"longestOutageMs\":7000"), json);
        assertTrue(json.contains("\"totalOutages\":1"), json);
        assertTrue(json.contains("\"sendErrors\":0"), json);
    }

    @Test
    void totalOutagesSumsAcrossNodes() {
        AtomicLong wall = new AtomicLong(0);
        UptimeTracker t = new UptimeTracker(1, wall::get);
        t.onPingComplete(1, 1000, Set.of("a/h1", "b/h2"));
        t.onPingComplete(2, 2000, Set.of());
        t.onPingComplete(3, 3000, Set.of("a/h1", "b/h2"));
        assertEquals(2, UptimeCommand.totalOutages(t.snapshot()));
    }

    @Test
    void stillDownNodeCountsAsOutage() {
        // Canonical CI scenario: a node goes DOWN and never recovers before the run ends.
        AtomicLong wall = new AtomicLong(0);
        UptimeTracker t = new UptimeTracker(1, wall::get);
        t.onPingComplete(1, 1000, Set.of("a/h1"));
        t.onPingComplete(2, 2000, Set.of());   // DOWN since 2000, never recovers
        wall.set(5000);
        assertTrue(UptimeCommand.totalOutages(t.snapshot()) >= 1,
                "a still-DOWN node must count as an outage so --fail-on-outage exits non-zero");
    }
}

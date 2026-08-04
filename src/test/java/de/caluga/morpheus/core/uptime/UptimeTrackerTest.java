package de.caluga.morpheus.core.uptime;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class UptimeTrackerTest {

    private final AtomicLong wall = new AtomicLong(0);

    private UptimeTracker tracker(int misses) {
        return new UptimeTracker(misses, wall::get);
    }

    @Test
    void learnsNodeOnFirstAnswer() {
        UptimeTracker t = tracker(3);
        t.onPingComplete(1, 1000, Set.of("a/h1"));
        var snap = t.snapshot();
        assertEquals(1, snap.nodes().size());
        var n = snap.nodes().get(0);
        assertEquals("a/h1", n.node());
        assertEquals(UptimeTracker.State.UP, n.state());
        assertEquals(1000, n.learnedTs());
        assertEquals(1000, n.stateSinceTs());
        assertEquals(1, snap.events().size());
        assertEquals(UptimeTracker.Event.Type.LEARNED, snap.events().get(0).type());
    }

    @Test
    void unknownNodesProduceNothing() {
        UptimeTracker t = tracker(3);
        t.onPingComplete(1, 1000, Set.of());
        assertTrue(t.snapshot().nodes().isEmpty());
        assertTrue(t.snapshot().events().isEmpty());
    }

    @Test
    void singleMissDoesNotGoDown() {
        UptimeTracker t = tracker(3);
        t.onPingComplete(1, 1000, Set.of("a/h1"));
        t.onPingComplete(2, 2000, Set.of());
        var n = t.snapshot().nodes().get(0);
        assertEquals(UptimeTracker.State.UP, n.state());
        assertEquals(0, n.outages());
    }

    @Test
    void answerResetsMissCounter() {
        UptimeTracker t = tracker(3);
        t.onPingComplete(1, 1000, Set.of("a/h1"));
        t.onPingComplete(2, 2000, Set.of());
        t.onPingComplete(3, 3000, Set.of());
        t.onPingComplete(4, 4000, Set.of("a/h1"));   // reset
        t.onPingComplete(5, 5000, Set.of());
        t.onPingComplete(6, 6000, Set.of());
        assertEquals(UptimeTracker.State.UP, t.snapshot().nodes().get(0).state());
    }

    @Test
    void downAfterThresholdIsBackdatedToFirstMiss() {
        UptimeTracker t = tracker(3);
        t.onPingComplete(1, 1000, Set.of("a/h1"));
        t.onPingComplete(2, 2000, Set.of());
        t.onPingComplete(3, 3000, Set.of());
        t.onPingComplete(4, 4000, Set.of());
        var snap = t.snapshot();
        var n = snap.nodes().get(0);
        assertEquals(UptimeTracker.State.DOWN, n.state());
        assertEquals(2000, n.stateSinceTs(), "DOWN must be backdated to the first missed ping");
        var down = snap.events().get(snap.events().size() - 1);
        assertEquals(UptimeTracker.Event.Type.DOWN, down.type());
        assertEquals(2000, down.ts());
    }

    @Test
    void recoveryEmitsUpWithDuration() {
        UptimeTracker t = tracker(3);
        t.onPingComplete(1, 1000, Set.of("a/h1"));
        t.onPingComplete(2, 2000, Set.of());
        t.onPingComplete(3, 3000, Set.of());
        t.onPingComplete(4, 4000, Set.of());
        t.onPingComplete(9, 9000, Set.of("a/h1"));
        var snap = t.snapshot();
        var n = snap.nodes().get(0);
        assertEquals(UptimeTracker.State.UP, n.state());
        assertEquals(1, n.outages());
        assertEquals(7000, n.longestOutageMs());
        assertEquals(7000, n.avgOutageMs());
        var up = snap.events().get(snap.events().size() - 1);
        assertEquals(UptimeTracker.Event.Type.UP, up.type());
        assertEquals(7000L, up.durationMs());
    }

    @Test
    void availabilityAccountsForOutageAndOpenDown() {
        UptimeTracker t = tracker(3);
        t.onPingComplete(1, 1000, Set.of("a/h1"));       // learned at 1000
        t.onPingComplete(2, 2000, Set.of());
        t.onPingComplete(3, 3000, Set.of());
        t.onPingComplete(4, 4000, Set.of());              // DOWN since 2000
        t.onPingComplete(9, 9000, Set.of("a/h1"));       // UP again, outage 7000ms
        wall.set(10_000);
        var n = t.snapshot().nodes().get(0);
        // observed 9000ms (1000..10000), down 7000ms -> 22.22%
        assertEquals(100.0 * (1.0 - 7000.0 / 9000.0), n.availabilityPct(), 0.01);
        // open outage counts into availability too:
        t.onPingComplete(10, 10_000, Set.of());
        t.onPingComplete(11, 11_000, Set.of());
        t.onPingComplete(12, 12_000, Set.of());           // DOWN since 10000
        wall.set(13_000);
        var n2 = t.snapshot().nodes().get(0);
        // observed 12000, down 7000 + (13000-10000) = 10000
        assertEquals(100.0 * (1.0 - 10_000.0 / 12_000.0), n2.availabilityPct(), 0.01);
    }

    @Test
    void timelineRecordsStateChanges() {
        UptimeTracker t = tracker(1);
        t.onPingComplete(1, 1000, Set.of("a/h1"));
        t.onPingComplete(2, 2000, Set.of());
        t.onPingComplete(3, 3000, Set.of("a/h1"));
        var tl = t.snapshot().timelines().get("a/h1");
        assertEquals(3, tl.size());
        assertEquals(UptimeTracker.State.UP, tl.get(0).state());
        assertEquals(UptimeTracker.State.DOWN, tl.get(1).state());
        assertEquals(2000, tl.get(1).ts());
        assertEquals(UptimeTracker.State.UP, tl.get(2).state());
    }

    @Test
    void sendErrorsAggregatePerSecond() {
        UptimeTracker t = tracker(3);
        wall.set(5000);
        for (int i = 0; i < 5; i++) t.recordSendError();
        var snap = t.snapshot();
        assertEquals(5, snap.sendErrors());
        assertEquals(1, snap.events().size(), "burst must aggregate into one event");
        wall.set(6001);
        t.recordSendError();
        assertEquals(2, t.snapshot().events().size());
        assertEquals(6, t.snapshot().sendErrors());
    }

    @Test
    void externalEventsRecorded() {
        UptimeTracker t = tracker(3);
        wall.set(42);
        t.recordEvent(UptimeTracker.Event.Type.PRIMARY_CHANGE, "rs", "h1 → h2");
        var e = t.snapshot().events().get(0);
        assertEquals(42, e.ts());
        assertEquals("rs", e.node());
        assertEquals("h1 → h2", e.detail());
    }

    @Test
    void eventsAreBounded() {
        UptimeTracker t = tracker(3);
        for (int i = 0; i < 1500; i++) t.recordEvent(UptimeTracker.Event.Type.PRIMARY_CHANGE, "rs", "x" + i);
        assertEquals(1000, t.snapshot().events().size());
        assertEquals("x1499", t.snapshot().events().get(999).detail());
    }

    @Test
    void missThresholdAdjustableAtRuntime() {
        UptimeTracker t = tracker(5);
        t.setMissThreshold(1);
        assertEquals(1, t.missThreshold());
        t.onPingComplete(1, 1000, Set.of("a/h1"));
        t.onPingComplete(2, 2000, Set.of());
        assertEquals(UptimeTracker.State.DOWN, t.snapshot().nodes().get(0).state());
    }

    @Test
    void resetClearsEverything() {
        UptimeTracker t = tracker(3);
        t.onPingComplete(1, 1000, Set.of("a/h1"));
        t.recordSendError();
        t.reset();
        var snap = t.snapshot();
        assertTrue(snap.nodes().isEmpty());
        assertTrue(snap.events().isEmpty());
        assertEquals(0, snap.sendErrors());
    }
}

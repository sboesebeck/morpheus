package de.caluga.morpheus.core.uptime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ReplicaSetWatcherTest {

    private final AtomicLong wall = new AtomicLong(0);
    private final UptimeTracker tracker = new UptimeTracker(3, wall::get);
    private final AtomicReference<String> primary = new AtomicReference<>("h1:27017");
    private final AtomicReference<Exception> fail = new AtomicReference<>(null);

    private ReplicaSetWatcher watcher() {
        return new ReplicaSetWatcher(() -> {
            if (fail.get() != null) throw fail.get();
            return primary.get();
        }, tracker, 50);
    }

    private java.util.List<UptimeTracker.Event> events() {
        return tracker.snapshot().events();
    }

    @Test
    void firstDiscoveryEmitsPrimaryChange() {
        ReplicaSetWatcher w = watcher();
        w.poll();
        assertEquals("h1:27017", w.primary());
        assertEquals(1, events().size());
        var e = events().get(0);
        assertEquals(UptimeTracker.Event.Type.PRIMARY_CHANGE, e.type());
        assertEquals("rs", e.node());
        assertEquals("? → h1:27017", e.detail());
    }

    @Test
    void samePrimaryEmitsNothing() {
        ReplicaSetWatcher w = watcher();
        w.poll();
        w.poll();
        w.poll();
        assertEquals(1, events().size());
    }

    @Test
    void primaryChangeEmitsOldAndNew() {
        ReplicaSetWatcher w = watcher();
        w.poll();
        primary.set("h2:27017");
        w.poll();
        assertEquals(2, events().size());
        assertEquals("h1:27017 → h2:27017", events().get(1).detail());
        assertEquals("h2:27017", w.primary());
    }

    @Test
    void failureEmitsUnknownOnceThenRecovers() {
        ReplicaSetWatcher w = watcher();
        w.poll();
        fail.set(new RuntimeException("connection refused"));
        w.poll();
        w.poll();
        w.poll();
        assertTrue(w.unknown());
        assertEquals(2, events().size(), "repeated failures must not spam");
        assertEquals(UptimeTracker.Event.Type.RS_UNKNOWN, events().get(1).type());
        fail.set(null);
        primary.set("h3:27017");
        w.poll();
        assertFalse(w.unknown());
        // RS_RECOVERED + PRIMARY_CHANGE
        assertEquals(4, events().size());
        assertEquals(UptimeTracker.Event.Type.RS_RECOVERED, events().get(2).type());
        assertEquals(UptimeTracker.Event.Type.PRIMARY_CHANGE, events().get(3).type());
        assertEquals("h1:27017 → h3:27017", events().get(3).detail());
    }

    @Test
    void nullPrimaryStaysSilent() {
        primary.set(null);
        ReplicaSetWatcher w = watcher();
        w.poll();
        w.poll();
        assertTrue(events().isEmpty(), "backend without primary field must not produce events");
        assertNull(w.primary());
    }

    @Test
    void startStopLifecycle() throws Exception {
        ReplicaSetWatcher w = watcher();
        w.start();
        Thread.sleep(200);
        w.stop();
        assertEquals("h1:27017", w.primary(), "background polling must have run");
        int n = events().size();
        Thread.sleep(120);
        assertEquals(n, events().size(), "no polling after stop");
        w.stop();   // idempotent
    }
}

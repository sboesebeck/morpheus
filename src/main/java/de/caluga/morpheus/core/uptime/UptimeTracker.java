package de.caluga.morpheus.core.uptime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/** Availability state machine over completed pings: learns responders on first answer,
 *  marks DOWN after N consecutive misses (backdated to the first miss), tracks outage
 *  statistics, a bounded event log and per-node state-change timelines. Thread-safe. */
public class UptimeTracker {

    public enum State { UP, DOWN }

    public record Event(long ts, Type type, String node, String detail, Long durationMs) {
        public enum Type { LEARNED, DOWN, UP, SEND_ERROR, PRIMARY_CHANGE, RS_UNKNOWN, RS_RECOVERED }
    }

    public record StateChange(long ts, State state) {}

    public record NodeStats(String node, State state, long stateSinceTs, long learnedTs,
                            double availabilityPct, int outages, long longestOutageMs, long avgOutageMs) {}

    public record Snapshot(long now, List<NodeStats> nodes, List<Event> events,
                           Map<String, List<StateChange>> timelines, long sendErrors) {}

    static final int MAX_EVENTS = 1000;
    static final int MAX_STATE_CHANGES = 200;
    static final long SEND_ERROR_WINDOW_MS = 1000;

    private static final class Node {
        State state = State.UP;
        long learnedTs, stateSinceTs;
        int missCount;
        long firstMissTs;
        int outages;
        long outageTotalMs, longestOutageMs;
        final ArrayDeque<StateChange> changes = new ArrayDeque<>();
    }

    private final LongSupplier wallClock;
    private volatile int missThreshold;
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private long sendErrors;
    private long sendErrorWindowStart = Long.MIN_VALUE;

    public UptimeTracker(int missThreshold, LongSupplier wallClock) {
        this.missThreshold = Math.max(1, missThreshold);
        this.wallClock = wallClock;
    }

    public int missThreshold() { return missThreshold; }
    public void setMissThreshold(int n) { missThreshold = Math.max(1, n); }

    public synchronized void onPingComplete(long seq, long sentTs, Set<String> answeredBy) {
        for (String key : answeredBy) {
            Node n = nodes.get(key);
            if (n == null) { learn(key, sentTs); continue; }
            if (n.state == State.DOWN) recover(key, n, sentTs);
            n.missCount = 0;
        }
        for (var e : nodes.entrySet()) {
            if (answeredBy.contains(e.getKey())) continue;
            Node n = e.getValue();
            if (n.missCount == 0) n.firstMissTs = sentTs;
            n.missCount++;
            if (n.state == State.UP && n.missCount >= missThreshold) goDown(e.getKey(), n);
        }
    }

    public synchronized void recordSendError() {
        long now = wallClock.getAsLong();
        sendErrors++;
        if (sendErrorWindowStart == Long.MIN_VALUE || now - sendErrorWindowStart >= SEND_ERROR_WINDOW_MS) {
            sendErrorWindowStart = now;
            addEvent(new Event(now, Event.Type.SEND_ERROR, "sender", "send error", null));
        }
    }

    /** External events (replica-set watcher etc.); timestamped with the tracker's clock. */
    public synchronized void recordEvent(Event.Type type, String node, String detail) {
        addEvent(new Event(wallClock.getAsLong(), type, node, detail, null));
    }

    public synchronized void reset() {
        nodes.clear();
        events.clear();
        sendErrors = 0;
        sendErrorWindowStart = Long.MIN_VALUE;
    }

    public synchronized Snapshot snapshot() {
        long now = wallClock.getAsLong();
        List<NodeStats> out = new ArrayList<>();
        Map<String, List<StateChange>> timelines = new LinkedHashMap<>();
        for (var e : nodes.entrySet()) {
            Node n = e.getValue();
            long observed = Math.max(1, now - n.learnedTs);
            long openMs = n.state == State.DOWN ? Math.max(0, now - n.stateSinceTs) : 0;
            long down = n.outageTotalMs + openMs;
            double avail = 100.0 * Math.max(0.0, 1.0 - (double) down / observed);
            // Fold a still-open outage into the reported stats so a node that goes DOWN and
            // never recovers is still visible in the summary/exit code; Node fields stay closed-only.
            int reportedOutages = n.state == State.DOWN ? n.outages + 1 : n.outages;
            long reportedLongest = n.state == State.DOWN ? Math.max(n.longestOutageMs, openMs) : n.longestOutageMs;
            long reportedAvg = reportedOutages == 0 ? 0 : (n.outageTotalMs + openMs) / reportedOutages;
            out.add(new NodeStats(e.getKey(), n.state, n.stateSinceTs, n.learnedTs,
                    avail, reportedOutages, reportedLongest, reportedAvg));
            timelines.put(e.getKey(), List.copyOf(n.changes));
        }
        return new Snapshot(now, out, List.copyOf(events), timelines, sendErrors);
    }

    private void learn(String key, long ts) {
        Node n = new Node();
        n.learnedTs = ts;
        n.stateSinceTs = ts;
        addChange(n, new StateChange(ts, State.UP));
        nodes.put(key, n);
        addEvent(new Event(ts, Event.Type.LEARNED, key, "", null));
    }

    private void goDown(String key, Node n) {
        n.state = State.DOWN;
        n.stateSinceTs = n.firstMissTs;
        addChange(n, new StateChange(n.firstMissTs, State.DOWN));
        addEvent(new Event(n.firstMissTs, Event.Type.DOWN, key, missThreshold + " Pings verpasst", null));
    }

    private void recover(String key, Node n, long ts) {
        long duration = Math.max(0, ts - n.stateSinceTs);
        n.outages++;
        n.outageTotalMs += duration;
        n.longestOutageMs = Math.max(n.longestOutageMs, duration);
        n.state = State.UP;
        n.stateSinceTs = ts;
        addChange(n, new StateChange(ts, State.UP));
        addEvent(new Event(ts, Event.Type.UP, key, "Ausfallzeit " + duration + "ms", duration));
    }

    private void addChange(Node n, StateChange c) {
        if (n.changes.size() >= MAX_STATE_CHANGES) n.changes.pollFirst();
        n.changes.addLast(c);
    }

    private void addEvent(Event e) {
        if (events.size() >= MAX_EVENTS) events.pollFirst();
        events.addLast(e);
    }
}

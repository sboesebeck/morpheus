package de.caluga.morpheus.core;

import java.util.LinkedHashSet;
import java.util.Set;

/** Selects which message flows produce a beam and which nodes stay bright in the
 *  graph view. Two independent axes — topics and nodes (sender ids). Empty on an
 *  axis means "pass everything" on that axis. OR within an axis, AND between axes.
 *  Pure and single-threaded (the TUI render thread); no backend dependency. */
public class GraphFilter {

    /** Active topic filter; empty = all topics pass. */
    public final Set<String> topics = new LinkedHashSet<>();
    /** Active node (sender-id) filter; empty = all nodes pass. */
    public final Set<String> nodes = new LinkedHashSet<>();

    public boolean isEmpty() {
        return topics.isEmpty() && nodes.isEmpty();
    }

    /** A flow yields a beam when it passes every active axis (AND between axes). */
    public boolean acceptsFlow(final FlowEvent f) {
        if (!topics.isEmpty() && !topics.contains(f.topic())) {
            return false;
        }
        if (!nodes.isEmpty() && !nodes.contains(f.from()) && !nodes.contains(f.to())) {
            return false;
        }
        return true;
    }

    /** A node is bright when an active axis considers it involved: it listens on an
     *  active topic, or it is in the active node set. No active filter -> all bright.
     *  Deliberately derived from the static filter + registry (not from in-flight
     *  shots) so nodes don't flicker between frames. */
    public boolean isHighlighted(final String nodeId, final NodeRegistry reg) {
        if (isEmpty()) {
            return true;
        }
        if (!topics.isEmpty()) {
            for (final String t : topics) {
                if (reg.listenersOf(t).contains(nodeId)) {
                    return true;
                }
            }
        }
        if (!nodes.isEmpty() && nodes.contains(nodeId)) {
            return true;
        }
        return false;
    }

    public void clear() {
        topics.clear();
        nodes.clear();
    }
}

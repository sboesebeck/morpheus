package de.caluga.morpheus.core.latency;

import java.util.Set;

/** Optional callback fired when a ping finishes its lifetime — either via timeout sweep
 *  or via pending-buffer eviction. answeredBy contains responderKey() strings (name/host)
 *  of every responder that answered before completion. Called under the probe monitor:
 *  implementations must be fast and must never call back into the probe. */
public interface PingOutcomeListener {
    void onPingComplete(long seq, long sentTs, Set<String> answeredBy);
    default void onSendError(long sentTs) {}
}

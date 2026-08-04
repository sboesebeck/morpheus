package de.caluga.morpheus.core.latency;

import java.util.Set;

/** Optional callback fired when a ping finishes its lifetime — either via timeout sweep,
 *  pending-buffer eviction, or send error. answeredBy contains responderKey() strings (name/host)
 *  of every responder that answered before completion.
 *
 *  <p>onPingComplete is called under the probe monitor: implementations must be fast and must
 *  never call back into the probe. onSendError is called outside the monitor and may run
 *  concurrently with onPingComplete — implementations needing shared state must synchronize. */
public interface PingOutcomeListener {
    void onPingComplete(long seq, long sentTs, Set<String> answeredBy);
    default void onSendError(long sentTs) {}
}

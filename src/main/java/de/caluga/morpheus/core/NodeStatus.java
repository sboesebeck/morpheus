package de.caluga.morpheus.core;

import java.util.Map;

/** One node's status-ping result: identity, RTT, key overview scalars, and the full raw answer map.
 *  Scalars are nullable — a node whose MORPHIUM section failed simply has null cache/driver metrics. */
public record NodeStatus(
        String sender, String host, long rttMs,
        Long heapUsed, Long heapMax,        // jvm.heap.used / jvm.heap.max
        Double cacheHitPct,                 // morphium.cachestats -> CHITSPERC
        Long connInUse, Long connInPool,    // morphium.driver.stats -> CONNECTIONS_IN_USE / CONNECTIONS_IN_POOL
        Long errors, Long msgSent,          // morphium.driver.stats -> ERRORS / MSG_SENT
        Integer threadsActive,              // jvm.threads.active
        Map<String, Object> raw) {          // the full answer map, for the detail view
}

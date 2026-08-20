package de.caluga.morpheus.core;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.MorphiumMessaging;
import de.caluga.morphium.messaging.Msg;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Sends one ALL status ping and parses each answer into a NodeStatus
 *  (identity, RTT, key overview scalars, and the full raw answer map). */
public class StatusPinger {

    private final MorphiumMessaging messaging;

    public StatusPinger(final MorphiumMessaging messaging) {
        this.messaging = messaging;
    }

    /** Sends an ALL ping; returns one NodeStatus per answer (sorted by sender). Never throws. */
    public List<NodeStatus> ping(final long timeoutMs) {
        try {
            final Msg msg = new Msg(messaging.getStatusInfoListenerName(), "ALL", "ALL", timeoutMs);
            msg.setMsgId(new MorphiumId());
            final long sendTime = System.currentTimeMillis();
            final var answers = messaging.sendAndAwaitAnswers(msg, 10000, timeoutMs);
            final List<NodeStatus> out = new ArrayList<>();
            for (final Msg a : answers) {
                out.add(parse(a, sendTime));
            }
            out.sort(Comparator.comparing(NodeStatus::sender, Comparator.nullsLast(Comparator.naturalOrder())));
            return out;
        } catch (final Exception e) {
            return List.of();
        }
    }

    /** Parses one answer into a NodeStatus. Defensive: missing sections leave their scalars null. */
    static NodeStatus parse(final Msg answer, final long sendTime) {
        final long rtt = Math.max(0, answer.getTimestamp() - sendTime);
        Map<String, Object> raw = answer.getMapValue();
        if (raw == null) {
            raw = Map.of();
        }

        final Long heapUsed = asLong(raw.get("jvm.heap.used"));
        final Long heapMax = asLong(raw.get("jvm.heap.max"));
        final Integer threadsActive = asInt(raw.get("jvm.threads.active"));

        final Map<String, Object> cache = asMap(raw.get("morphium.cachestats"));
        final Double cacheHitPct = cache == null ? null : asDouble(cache.get("CHITSPERC"));

        final Map<String, Object> drv = asMap(raw.get("morphium.driver.stats"));
        final Long connInUse = drv == null ? null : asLong(drv.get("CONNECTIONS_IN_USE"));
        final Long connInPool = drv == null ? null : asLong(drv.get("CONNECTIONS_IN_POOL"));
        final Long errors = drv == null ? null : asLong(drv.get("ERRORS"));
        final Long msgSent = drv == null ? null : asLong(drv.get("MSG_SENT"));

        return new NodeStatus(answer.getSender(), answer.getSenderHost(), rtt,
                heapUsed, heapMax, cacheHitPct, connInUse, connInPool, errors, msgSent, threadsActive, raw);
    }

    // ---- defensive coercion: wire values arrive as Double/Long/Integer ----
    static Long asLong(final Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }

    static Integer asInt(final Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    static Double asDouble(final Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(final Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }
}

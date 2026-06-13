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

    public StatusPinger(MorphiumMessaging messaging) {
        this.messaging = messaging;
    }

    /** Sends an ALL ping; returns one NodeStatus per answer (sorted by sender). Never throws. */
    public List<NodeStatus> ping(long timeoutMs) {
        try {
            Msg msg = new Msg(messaging.getStatusInfoListenerName(), "ALL", "ALL", timeoutMs);
            msg.setMsgId(new MorphiumId());
            long sendTime = System.currentTimeMillis();
            var answers = messaging.sendAndAwaitAnswers(msg, 10000, timeoutMs);
            List<NodeStatus> out = new ArrayList<>();
            for (Msg a : answers) out.add(parse(a, sendTime));
            out.sort(Comparator.comparing(NodeStatus::sender, Comparator.nullsLast(Comparator.naturalOrder())));
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Parses one answer into a NodeStatus. Defensive: missing sections leave their scalars null. */
    static NodeStatus parse(Msg answer, long sendTime) {
        long rtt = Math.max(0, answer.getTimestamp() - sendTime);
        Map<String, Object> raw = answer.getMapValue();
        if (raw == null) raw = Map.of();

        Long heapUsed = asLong(raw.get("jvm.heap.used"));
        Long heapMax = asLong(raw.get("jvm.heap.max"));
        Integer threadsActive = asInt(raw.get("jvm.threads.active"));

        Map<String, Object> cache = asMap(raw.get("morphium.cachestats"));
        Double cacheHitPct = cache == null ? null : asDouble(cache.get("CHITSPERC"));

        Map<String, Object> drv = asMap(raw.get("morphium.driver.stats"));
        Long connInUse = drv == null ? null : asLong(drv.get("CONNECTIONS_IN_USE"));
        Long connInPool = drv == null ? null : asLong(drv.get("CONNECTIONS_IN_POOL"));
        Long errors = drv == null ? null : asLong(drv.get("ERRORS"));
        Long msgSent = drv == null ? null : asLong(drv.get("MSG_SENT"));

        return new NodeStatus(answer.getSender(), answer.getSenderHost(), rtt,
                heapUsed, heapMax, cacheHitPct, connInUse, connInPool, errors, msgSent, threadsActive, raw);
    }

    // ---- defensive coercion: wire values arrive as Double/Long/Integer ----
    static Long asLong(Object o) { return o instanceof Number n ? n.longValue() : null; }
    static Integer asInt(Object o) { return o instanceof Number n ? n.intValue() : null; }
    static Double asDouble(Object o) { return o instanceof Number n ? n.doubleValue() : null; }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) { return o instanceof Map<?, ?> m ? (Map<String, Object>) m : null; }
}

package de.caluga.morpheus.core;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class StatusPingerTest {

    private Msg answer(String sender, String host, long ts) {
        Msg a = new Msg("x", "m", "v", 1000);
        a.setMsgId(new MorphiumId());
        a.setSender(sender);
        a.setSenderHost(host);
        a.setTimestamp(ts);
        return a;
    }

    @Test
    void parseExtractsScalarsAndKeepsRaw() {
        Msg a = answer("worker1", "h1", 1500);
        Map<String, Object> map = new HashMap<>();
        map.put("jvm.heap.used", 100L);
        map.put("jvm.heap.max", 400L);
        map.put("jvm.threads.active", 42);              // Integer
        Map<String, Object> cache = new HashMap<>();
        cache.put("CHITSPERC", 97.5);                   // Double
        map.put("morphium.cachestats", cache);
        Map<String, Object> drv = new HashMap<>();
        drv.put("CONNECTIONS_IN_USE", 3.0);             // Double on the wire
        drv.put("CONNECTIONS_IN_POOL", 10.0);
        drv.put("ERRORS", 0.0);
        drv.put("MSG_SENT", 1234.0);
        map.put("morphium.driver.stats", drv);
        a.setMapValue(map);

        NodeStatus s = StatusPinger.parse(a, 1480);
        assertEquals("worker1", s.sender());
        assertEquals("h1", s.host());
        assertEquals(20, s.rttMs());
        assertEquals(100L, (long) s.heapUsed());
        assertEquals(400L, (long) s.heapMax());
        assertEquals(42, (int) s.threadsActive());
        assertEquals(97.5, s.cacheHitPct(), 0.0001);
        assertEquals(3L, (long) s.connInUse());
        assertEquals(10L, (long) s.connInPool());
        assertEquals(0L, (long) s.errors());
        assertEquals(1234L, (long) s.msgSent());
        assertSame(map, s.raw());
    }

    @Test
    void parseHandlesMissingMorphiumSection() {
        Msg a = answer("worker2", "h2", 1500);
        Map<String, Object> map = new HashMap<>();
        map.put("jvm.heap.used", 50L);
        map.put("jvm.heap.max", 200L);
        map.put("jvm.threads.active", 7);
        a.setMapValue(map);

        NodeStatus s = StatusPinger.parse(a, 1480);
        assertEquals(50L, (long) s.heapUsed());
        assertEquals(7, (int) s.threadsActive());
        assertNull(s.cacheHitPct(), "no cachestats -> null");
        assertNull(s.connInUse(), "no driver stats -> null");
        assertNull(s.errors());
        assertNull(s.msgSent());
    }

    @Test
    void parseHandlesNullMapValue() {
        Msg a = answer("w3", "h3", 1100);
        NodeStatus s = StatusPinger.parse(a, 1000);
        assertEquals(100, s.rttMs());
        assertNull(s.heapUsed());
        assertNull(s.cacheHitPct());
        assertTrue(s.raw().isEmpty());
    }
}

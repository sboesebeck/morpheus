package de.caluga.morpheus.core;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class StatusPingerTest {

    @Test
    void parseExtractsSenderHostRttAndTopics() {
        Msg answer = new Msg("x", "m", "v", 1000);
        answer.setMsgId(new MorphiumId());
        answer.setSender("worker1");
        answer.setSenderHost("h1");
        answer.setTimestamp(1500);
        Map<String, Object> map = new HashMap<>();
        Map<String, Object> listeners = new HashMap<>();
        listeners.put("order.created", List.of("OrderListener"));
        listeners.put("order.shipped", List.of("ShipListener"));
        map.put("message_listeners_by_name", listeners);
        answer.setMapValue(map);

        StatusPinger.NodeEntry e = StatusPinger.parse(answer, 1480);
        assertEquals("worker1", e.sender());
        assertEquals("h1", e.host());
        assertEquals(20, e.rttMs());
        assertTrue(e.topics().contains("order.created"));
        assertTrue(e.topics().contains("order.shipped"));
        assertEquals(2, e.topics().size());
    }

    @Test
    void parseHandlesMissingListeners() {
        Msg answer = new Msg("x", "m", "v", 1000);
        answer.setMsgId(new MorphiumId());
        answer.setSender("worker2");
        answer.setSenderHost("h2");
        answer.setTimestamp(900);
        answer.setMapValue(new HashMap<>());

        StatusPinger.NodeEntry e = StatusPinger.parse(answer, 1000);
        assertEquals("worker2", e.sender());
        assertEquals(0, e.rttMs(), "negative skew clamps to 0");
        assertTrue(e.topics().isEmpty());
    }

    @Test
    void parseHandlesNullMapValue() {
        Msg answer = new Msg("x", "m", "v", 1000);
        answer.setMsgId(new MorphiumId());
        answer.setSender("w3");
        answer.setSenderHost("h3");
        answer.setTimestamp(1100);
        StatusPinger.NodeEntry e = StatusPinger.parse(answer, 1000);
        assertTrue(e.topics().isEmpty());
        assertEquals(100, e.rttMs());
    }
}

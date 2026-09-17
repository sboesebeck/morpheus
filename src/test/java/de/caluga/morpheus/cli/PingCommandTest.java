package de.caluga.morpheus.cli;

import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PingCommandTest {

    private Msg answer(String sender, String host, long timestamp, Map<String, Object> payload) {
        Msg m = new Msg();
        m.setSender(sender);
        m.setSenderHost(host);
        m.setTimestamp(timestamp);
        if (payload != null) {
            m.setMapValue(payload);
        }
        return m;
    }

    @Test
    void includesNestedPayloadForAllLevel() {
        Map<String, Object> cache = new LinkedHashMap<>();
        cache.put("CHITSPERC", 98.2);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jvm.heap.used", 123456L);
        payload.put("morphium.cachestats", cache);
        List<Msg> answers = List.of(answer("node1", "h1.local", 1012, payload));

        String json = PingCommand.reportJson(1000, 30, PingCommand.Level.ALL, 10000, answers);

        assertTrue(json.contains("\"sender\":\"node1\""), json);
        assertTrue(json.contains("\"host\":\"h1.local\""), json);
        assertTrue(json.contains("\"rttMs\":12"), json);
        assertTrue(json.contains("\"jvm.heap.used\":123456"), json);
        assertTrue(json.contains("\"morphium.cachestats\":{\"CHITSPERC\":98.2}"), json);
        assertTrue(json.contains("\"level\":\"ALL\""), json);
        assertTrue(json.contains("\"answerCount\":1"), json);
    }

    @Test
    void omitsPayloadForPingLevel() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jvm.heap.used", 1L);
        List<Msg> answers = List.of(answer("node1", "h1", 1000, payload));

        String json = PingCommand.reportJson(1000, 5, PingCommand.Level.PING, 10, answers);

        assertTrue(json.contains("\"payload\":null"), json);
        assertFalse(json.contains("jvm.heap.used"), json);
    }

    @Test
    void escapesSpecialCharactersInStrings() {
        List<Msg> answers = List.of(answer("node\"1", "h1\nline2", 1000, null));

        String json = PingCommand.reportJson(1000, 5, PingCommand.Level.ALL, 10, answers);

        assertTrue(json.contains("\"sender\":\"node\\\"1\""), json);
        assertTrue(json.contains("\"host\":\"h1\\nline2\""), json);
    }

    @Test
    void emptyAnswersProducesEmptyArray() {
        String json = PingCommand.reportJson(1000, 5, PingCommand.Level.ALL, 10, List.of());

        assertTrue(json.contains("\"answerCount\":0"), json);
        assertTrue(json.contains("\"answers\":[]"), json);
    }
}

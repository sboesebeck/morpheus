package de.caluga.morpheus.core;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MessageInfoTest {

    private Msg msg(String topic, String text) {
        Msg m = new Msg(topic, text, "v", 30000);
        m.setMsgId(new MorphiumId());
        m.setSender("s"); m.setSenderHost("h");
        m.setTimestamp(System.currentTimeMillis());
        return m;
    }

    @Test
    void capturesBodyFromMsgAndValue() {
        MessageInfo info = new MessageInfo(msg("orders", "hello world"), "orders");
        assertNotNull(info.body);
        assertTrue(info.body.contains("hello world"), "msg text captured");
        assertTrue(info.body.contains("v"), "value captured");
    }

    @Test
    void capsBodyAt2048Chars() {
        String big = "x".repeat(5000);
        MessageInfo info = new MessageInfo(msg("orders", big), "orders");
        assertEquals(2048, info.body.length(), "body capped at MAX_BODY_CAPTURE");
    }

    @Test
    void v5MessageWithoutTopicDoesNotThrow() {
        Msg m = new Msg(null, "v5text", "v", 30000);   // V5: no topic
        m.setMsgId(new MorphiumId());
        m.setTimestamp(System.currentTimeMillis());
        MessageInfo info = new MessageInfo(m, "");      // must not throw
        assertNotNull(info.body);
    }
}

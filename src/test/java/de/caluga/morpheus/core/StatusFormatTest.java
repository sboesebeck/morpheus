package de.caluga.morpheus.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StatusFormatTest {

    @Test
    void humanBytesScalesAndHandlesNull() {
        assertEquals("–", StatusFormat.humanBytes(null));
        assertEquals("900B", StatusFormat.humanBytes(900L));
        assertEquals("1.0K", StatusFormat.humanBytes(1024L));
        assertEquals("1.0M", StatusFormat.humanBytes(1024L * 1024));
        assertEquals("1.0G", StatusFormat.humanBytes(1024L * 1024 * 1024));
    }

    @Test
    void pctFormatsAndHandlesNull() {
        assertEquals("–", StatusFormat.pct(null));
        assertEquals("97.3%", StatusFormat.pct(97.34));
    }
}

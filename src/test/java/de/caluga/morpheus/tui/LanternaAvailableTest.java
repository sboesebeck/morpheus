package de.caluga.morpheus.tui;

import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LanternaAvailableTest {
    @Test
    void virtualTerminalConstructs() {
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal();
        assertNotNull(vt.getTerminalSize());
    }
}

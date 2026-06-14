package de.caluga.morpheus.tui.widget;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BrailleCanvasTest {

    @Test
    void plotsMapToBrailleBits() {
        BrailleCanvas c = new BrailleCanvas(1, 1);     // 2x4 subpixels
        c.plot(0, 0, TextColor.ANSI.RED);              // top-left dot -> 0x01
        assertEquals((char) 0x2801, c.glyphAt(0, 0));
        c.plot(1, 3, TextColor.ANSI.RED);              // dx=1,dy=3 -> 0x80
        assertEquals((char) (0x2801 | 0x80), c.glyphAt(0, 0));
        assertEquals(TextColor.ANSI.RED, c.colorAt(0, 0));
    }

    @Test
    void outOfBoundsPlotIgnored() {
        BrailleCanvas c = new BrailleCanvas(1, 1);
        c.plot(-1, 0, TextColor.ANSI.RED);
        c.plot(99, 0, TextColor.ANSI.RED);
        assertEquals((char) 0x2800, c.glyphAt(0, 0));
    }

    @Test
    void linePlotsBothEndpoints() {
        BrailleCanvas c = new BrailleCanvas(4, 4);     // 8x16 subpixels
        c.line(0, 0, 7, 15, TextColor.ANSI.GREEN);
        assertNotEquals((char) 0x2800, c.glyphAt(0, 0));
        assertNotEquals((char) 0x2800, c.glyphAt(3, 3));
    }

    @Test
    void clearResets() {
        BrailleCanvas c = new BrailleCanvas(1, 1);
        c.plot(0, 0, TextColor.ANSI.RED);
        c.clear();
        assertEquals((char) 0x2800, c.glyphAt(0, 0));
        assertNull(c.colorAt(0, 0));
    }

    @Test
    void renderDoesNotThrow() throws Exception {
        BrailleCanvas c = new BrailleCanvas(10, 5);
        c.line(0, 0, 19, 19, TextColor.ANSI.CYAN);
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new com.googlecode.lanterna.TerminalSize(40, 20));
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        c.render(ts.newTextGraphics(), 0, 0);
        ts.stopScreen();
    }
}

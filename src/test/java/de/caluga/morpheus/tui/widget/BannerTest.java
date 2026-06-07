package de.caluga.morpheus.tui.widget;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BannerTest {

    @Test
    void gradientEndpoints() {
        TextColor.RGB top = Banner.lineColor(0, 5);
        TextColor.RGB bottom = Banner.lineColor(4, 5);
        assertEquals(0, top.getRed());
        assertEquals(80, top.getGreen());
        assertEquals(0, top.getBlue());
        assertEquals(140, bottom.getRed());
        assertEquals(255, bottom.getGreen());
        assertEquals(140, bottom.getBlue());
    }

    @Test
    void greenChannelIncreasesMonotonically() {
        int prev = -1;
        for (int i = 0; i < 5; i++) {
            int g = Banner.lineColor(i, 5).getGreen();
            assertTrue(g >= prev, "green must not decrease down the gradient");
            prev = g;
        }
    }

    @Test
    void singleLineDoesNotDivideByZero() {
        TextColor.RGB only = Banner.lineColor(0, 1);
        assertEquals(0, only.getRed());
        assertEquals(80, only.getGreen());
    }

    @Test
    void hasFiveLinesAndDrawsWithoutError() throws Exception {
        assertEquals(5, Banner.LINES.length);
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal();
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        Banner.draw(ts.newTextGraphics(), 2, 0); // must not throw
        ts.stopScreen();
    }
}

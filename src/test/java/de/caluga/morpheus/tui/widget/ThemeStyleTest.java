package de.caluga.morpheus.tui.widget;

import com.googlecode.lanterna.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ThemeStyleTest {
    @Test
    void semanticTagsMap() {
        assertEquals(TextColor.ANSI.GREEN, ThemeStyle.color("good"));
        assertEquals(TextColor.ANSI.RED, ThemeStyle.color("error"));
        assertTrue(ThemeStyle.bold("error"));
        assertEquals(TextColor.ANSI.YELLOW, ThemeStyle.color("warning"));
        assertTrue(ThemeStyle.bold("header1"));
    }

    @Test
    void unknownTagIsDefaultNotBold() {
        assertEquals(TextColor.ANSI.DEFAULT, ThemeStyle.color("nope"));
        assertFalse(ThemeStyle.bold("nope"));
    }

    @Test
    void resetIsDefault() {
        assertEquals(TextColor.ANSI.DEFAULT, ThemeStyle.color("r"));
    }
}

package de.caluga.morpheus.tui.widget;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class FieldTest {
    @Test
    void typingAppends() {
        Field f = new Field("Name", "", Field.Type.TEXT);
        f.type('a'); f.type('b');
        assertEquals("ab", f.value());
    }

    @Test
    void backspaceRemovesLast() {
        Field f = new Field("Name", "ab", Field.Type.TEXT);
        f.backspace();
        assertEquals("a", f.value());
        f.backspace(); f.backspace();
        assertEquals("", f.value(), "backspace on empty is a no-op");
    }

    @Test
    void passwordDisplaysMasked() {
        Field f = new Field("Pw", "secret", Field.Type.PASSWORD);
        assertEquals("••••••", f.display());
        assertEquals("secret", f.value());
    }
}

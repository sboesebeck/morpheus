package de.caluga.morpheus.tui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ScreenStackTest {

    /** Minimal Screen that records nothing; draw/onKey are unused in stack tests. */
    private Screen scr(String id) {
        return new Screen() {
            public void draw(com.googlecode.lanterna.graphics.TextGraphics g) {}
            public Result onKey(com.googlecode.lanterna.input.KeyStroke k) { return Result.stay(); }
            public String toString() { return id; }
        };
    }

    @Test
    void pushAndTop() {
        ScreenStack s = new ScreenStack();
        assertTrue(s.isEmpty());
        Screen a = scr("a");
        s.push(a);
        assertSame(a, s.top());
        assertEquals(1, s.size());
    }

    @Test
    void applyPushAddsScreen() {
        ScreenStack s = new ScreenStack();
        s.push(scr("a"));
        Screen b = scr("b");
        s.apply(Screen.Result.push(b));
        assertSame(b, s.top());
        assertEquals(2, s.size());
    }

    @Test
    void applyPopRemovesTop() {
        ScreenStack s = new ScreenStack();
        Screen a = scr("a");
        s.push(a);
        s.push(scr("b"));
        s.apply(Screen.Result.pop());
        assertSame(a, s.top());
        assertEquals(1, s.size());
    }

    @Test
    void applyReplaceSwapsTop() {
        ScreenStack s = new ScreenStack();
        s.push(scr("a"));
        Screen c = scr("c");
        s.apply(Screen.Result.replace(c));
        assertSame(c, s.top());
        assertEquals(1, s.size());
    }

    @Test
    void applyStayLeavesStackUnchanged() {
        ScreenStack s = new ScreenStack();
        Screen a = scr("a");
        s.push(a);
        s.apply(Screen.Result.stay());
        assertSame(a, s.top());
        assertEquals(1, s.size());
    }

    @Test
    void resultKindsAreDistinct() {
        assertEquals(Screen.Result.Kind.QUIT, Screen.Result.quit().kind());
        assertEquals(Screen.Result.Kind.PUSH, Screen.Result.push(scr("x")).kind());
        assertSame(null, Screen.Result.pop().next());
    }
}

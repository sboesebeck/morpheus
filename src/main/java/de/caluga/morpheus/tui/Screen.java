package de.caluga.morpheus.tui;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;

/** One full-screen view. Draws itself and reacts to a key by returning a navigation Result. */
public interface Screen {
    void draw(TextGraphics g);
    Result onKey(KeyStroke key);

    /** Navigation outcome of a key press. */
    final class Result {
        public enum Kind { STAY, POP, QUIT, PUSH, REPLACE }
        private final Kind kind;
        private final Screen next;

        private Result(Kind kind, Screen next) {
            this.kind = kind;
            this.next = next;
        }

        public static Result stay()            { return new Result(Kind.STAY, null); }
        public static Result pop()             { return new Result(Kind.POP, null); }
        public static Result quit()            { return new Result(Kind.QUIT, null); }
        public static Result push(Screen s)    { return new Result(Kind.PUSH, s); }
        public static Result replace(Screen s) { return new Result(Kind.REPLACE, s); }

        public Kind kind()   { return kind; }
        public Screen next() { return next; }
    }
}

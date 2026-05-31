package de.caluga.morpheus.tui;

import java.util.ArrayDeque;
import java.util.Deque;

/** Holds the screen stack and applies navigation Results to it. Pure logic, no rendering. */
public class ScreenStack {
    private final Deque<Screen> stack = new ArrayDeque<>();

    public void push(Screen s) { stack.push(s); }
    public Screen pop()        { return stack.pop(); }
    public Screen top()        { return stack.peek(); }
    public boolean isEmpty()   { return stack.isEmpty(); }
    public int size()          { return stack.size(); }

    /** Mutates the stack per the Result. STAY and QUIT do not change it. */
    public void apply(Screen.Result r) {
        switch (r.kind()) {
            case PUSH    -> push(r.next());
            case POP     -> { if (!stack.isEmpty()) pop(); }
            case REPLACE -> { if (!stack.isEmpty()) pop(); push(r.next()); }
            case STAY, QUIT -> { /* no stack change */ }
        }
    }
}

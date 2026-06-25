package de.caluga.morpheus.core;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Pure filter logic for the message view. Value axes (topic/sender/host) use OR within an
 *  axis and AND between axes; status axes are tri-state; body is matched by a case-insensitive
 *  regex. UI-free and fully unit-testable. */
public final class MessageFilter {

    public enum TriState { NONE, ONLY, EXCLUDE }

    public final Set<String> topics  = new LinkedHashSet<>();
    public final Set<String> senders = new LinkedHashSet<>();
    public final Set<String> hosts   = new LinkedHashSet<>();
    public String bodyRegex;                       // null/empty = inactive
    public TriState answered  = TriState.NONE;
    public TriState timedOut  = TriState.NONE;
    public TriState exclusive = TriState.NONE;
    public TriState deleted   = TriState.NONE;

    public boolean isEmpty() {
        return topics.isEmpty() && senders.isEmpty() && hosts.isEmpty()
                && (bodyRegex == null || bodyRegex.isEmpty())
                && answered == TriState.NONE && timedOut == TriState.NONE
                && exclusive == TriState.NONE && deleted == TriState.NONE;
    }

    public boolean accepts(MessageInfo m) {
        if (!topics.isEmpty()  && !topics.contains(m.topic)) return false;
        if (!senders.isEmpty() && !senders.contains(m.sender)) return false;
        if (!hosts.isEmpty()   && !hosts.contains(m.senderHost)) return false;
        if (!matchTri(answered,  m.rtt != null)) return false;
        if (!matchTri(timedOut,  m.isTimedOut)) return false;
        if (!matchTri(exclusive, m.isExclusive)) return false;
        if (!matchTri(deleted,   m.isDeleted)) return false;
        Pattern p = compiled();
        if (p != null && (m.body == null || !p.matcher(m.body).find())) return false;
        return true;
    }

    private static boolean matchTri(TriState t, boolean value) {
        return switch (t) {
            case NONE -> true;
            case ONLY -> value;
            case EXCLUDE -> !value;
        };
    }

    /** Compiles bodyRegex case-insensitively; returns null when empty or invalid. */
    private Pattern compiled() {
        if (bodyRegex == null || bodyRegex.isEmpty()) return null;
        try {
            return Pattern.compile(bodyRegex, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    public boolean regexValid() {
        return bodyRegex == null || bodyRegex.isEmpty() || compiled() != null;
    }

    public void cycle(String axis) {
        switch (axis) {
            case "answered"  -> answered  = next(answered);
            case "timedOut"  -> timedOut  = next(timedOut);
            case "exclusive" -> exclusive = next(exclusive);
            case "deleted"   -> deleted   = next(deleted);
            default -> { }
        }
    }

    private static TriState next(TriState t) {
        return switch (t) {
            case NONE -> TriState.ONLY;
            case ONLY -> TriState.EXCLUDE;
            case EXCLUDE -> TriState.NONE;
        };
    }

    public void clear() {
        topics.clear(); senders.clear(); hosts.clear();
        bodyRegex = null;
        answered = timedOut = exclusive = deleted = TriState.NONE;
    }
}

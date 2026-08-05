package de.caluga.morpheus.tui;

/** Small text helpers shared by the TUI screens. */
public final class TuiText {

    private TuiText() { }

    /** Shortens an auto-generated id (UUID / object id) to head…tail; keeps short readable names as-is. */
    public static String shortId(String id) {
        if (id == null) return "?";
        if (id.length() <= 16) return id;
        return id.substring(0, 6) + "…" + id.substring(id.length() - 4);
    }

    /** Truncates to max columns with a trailing ellipsis. */
    public static String trunc(String s, int max) {
        if (s == null) return "";
        if (max <= 1) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

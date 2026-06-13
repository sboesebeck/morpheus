package de.caluga.morpheus.core;

import java.util.Locale;

/** Small, locale-stable display formatters for status metrics. */
public final class StatusFormat {

    private StatusFormat() {}

    /** Human-readable bytes: 900B / 1.0K / 1.0M / 1.0G. Null → "–". */
    public static String humanBytes(Long bytes) {
        if (bytes == null) return "–";
        double b = bytes;
        if (b < 1024) return bytes + "B";
        if (b < 1024.0 * 1024) return String.format(Locale.ROOT, "%.1fK", b / 1024.0);
        if (b < 1024.0 * 1024 * 1024) return String.format(Locale.ROOT, "%.1fM", b / (1024.0 * 1024));
        return String.format(Locale.ROOT, "%.1fG", b / (1024.0 * 1024 * 1024));
    }

    /** Percent with one decimal: 97.3%. Null → "–". */
    public static String pct(Double p) {
        return p == null ? "–" : String.format(Locale.ROOT, "%.1f%%", p);
    }
}

package de.caluga.morpheus.core;

import java.util.Locale;

/** Small, locale-stable display formatters for status metrics. */
public final class StatusFormat {

    private static final long KILO = 1024L;
    private static final long MEGA = KILO * KILO;
    private static final long GIGA = MEGA * KILO;
    private static final double KILO_D = 1024.0;
    private static final double MEGA_D = KILO_D * KILO_D;
    private static final double GIGA_D = MEGA_D * KILO_D;

    private StatusFormat() {}

    /** Human-readable bytes: 900B / 1.0K / 1.0M / 1.0G. Null → "–". */
    public static String humanBytes(final Long bytes) {
        if (bytes == null) {
            return "–";
        }
        final double b = bytes;
        if (b < KILO) {
            return bytes + "B";
        }
        if (b < MEGA_D) {
            return String.format(Locale.ROOT, "%.1fK", b / KILO_D);
        }
        if (b < GIGA_D) {
            return String.format(Locale.ROOT, "%.1fM", b / MEGA_D);
        }
        return String.format(Locale.ROOT, "%.1fG", b / GIGA_D);
    }

    /** Percent with one decimal: 97.3%. Null → "–". */
    public static String pct(final Double p) {
        return p == null ? "–" : String.format(Locale.ROOT, "%.1f%%", p);
    }
}

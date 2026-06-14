package de.caluga.morpheus.tui.widget;

import java.io.IOException;
import java.util.Base64;
import java.util.function.Function;

/** Kitty graphics protocol: capability detection + escape emission for a single, replaced-in-place image.
 *  The per-frame "delete by id then transmit+display" idiom (with a fixed image id) animates portably on
 *  Kitty, Ghostty and WezTerm (validated by spike). All output goes to an Appendable so it is testable. */
public final class KittyGraphics {

    private static final int CHUNK = 4096;
    private static final String ESC = "\033";
    private static final String ST = "\033\\";          // APC string terminator (ESC \)
    private static final String DELETE = ESC + "_Ga=d,d=I,i=1,q=2" + ST;

    private KittyGraphics() {}

    /** True when the environment looks Kitty-graphics capable (Kitty / WezTerm / Ghostty). */
    public static boolean supported() {
        return supported(System::getenv);
    }

    static boolean supported(Function<String, String> env) {
        if (notEmpty(env.apply("KITTY_WINDOW_ID"))) return true;
        if ("WezTerm".equals(env.apply("TERM_PROGRAM"))) return true;
        if (notEmpty(env.apply("GHOSTTY_RESOURCES_DIR")) || notEmpty(env.apply("GHOSTTY_BIN_DIR"))) return true;
        String term = env.apply("TERM");
        return term != null && (term.contains("kitty") || term.contains("ghostty"));
    }

    /** Replaces the current image (id 1) with the given PNG, displayed at startRow;startCol scaled to cols x rows cells. */
    public static void emit(byte[] png, int cols, int rows, int startRow, int startCol, Appendable out) {
        String b64 = Base64.getEncoder().encodeToString(png);
        StringBuilder sb = new StringBuilder();
        sb.append(ESC).append('[').append(startRow).append(';').append(startCol).append('H');   // cursor to the region
        sb.append(DELETE);                                                                       // drop the previous frame
        int n = b64.length();
        for (int i = 0; i < n; i += CHUNK) {
            int end = Math.min(n, i + CHUNK);
            boolean first = i == 0;
            boolean last = end == n;
            sb.append(ESC).append("_G");
            if (first) {
                sb.append("a=T,f=100,i=1,q=2,C=1,c=").append(cols).append(",r=").append(rows).append(',');
            }
            sb.append("m=").append(last ? 0 : 1).append(';').append(b64, i, end).append(ST);
        }
        append(out, sb);
    }

    /** Removes the image (toggle-off / teardown). */
    public static void delete(Appendable out) {
        append(out, DELETE);
    }

    private static void append(Appendable out, CharSequence s) {
        try {
            out.append(s);
            if (out instanceof java.io.Flushable f) f.flush();
        } catch (IOException ignored) {
            // System.out never throws here; a test StringBuilder never throws.
        }
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}

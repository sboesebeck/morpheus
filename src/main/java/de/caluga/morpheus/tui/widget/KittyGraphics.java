package de.caluga.morpheus.tui.widget;

import java.io.IOException;
import java.util.Base64;
import java.util.function.Function;

/** Kitty graphics protocol: capability detection + escape emission for a double-buffered, replaced-in-place image.
 *  Per frame we transmit+display the NEW image (it covers the previous one), THEN delete the previous image —
 *  so the cell region is never blank between frames. This kills the flicker the older "delete-first" idiom
 *  showed under load: with delete-first the region stayed empty while the new (larger) PNG was still being
 *  transmitted/decoded, a gap that grew with the image size. Frames alternate between two image ids so the
 *  new frame can be shown before its predecessor is freed. Animates portably on Kitty, Ghostty and WezTerm.
 *  All output goes to an Appendable so it is testable. */
public final class KittyGraphics {

    private static final int CHUNK = 4096;
    private static final String ESC = "\033";
    private static final String ST = "\033\\";          // APC string terminator (ESC \)
    private static final int[] IDS = {1, 2};            // double buffer: even frames use id 1, odd frames id 2

    private KittyGraphics() {}

    private static String deleteById(int id) {
        return ESC + "_Ga=d,d=I,i=" + id + ",q=2" + ST;
    }

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

    /** Shows the given frame (zlib-compressed raw RGB, {@code pxW}×{@code pxH} px) at startRow;startCol scaled to
     *  cols × rows cells, then frees the previous frame. {@code frame} is a monotonically increasing counter
     *  (0,1,2,…) selecting the double-buffer image id so the new frame is placed on top of its predecessor before
     *  the predecessor is deleted (no blank gap). */
    public static void emit(byte[] rgbz, int pxW, int pxH, int cols, int rows, int startRow, int startCol, int frame, Appendable out) {
        int cur = IDS[frame & 1];
        int prev = IDS[(frame + 1) & 1];
        String b64 = Base64.getEncoder().encodeToString(rgbz);
        StringBuilder sb = new StringBuilder();
        sb.append(ESC).append('[').append(startRow).append(';').append(startCol).append('H');   // cursor to the region
        // Transmit+display the new frame first; at equal z-index the newer placement renders on top, covering prev.
        // f=24 raw RGB, o=z zlib-compressed, s/v = source pixel size, c/r = cells to fill (scaled by the terminal).
        int n = b64.length();
        for (int i = 0; i < n; i += CHUNK) {
            int end = Math.min(n, i + CHUNK);
            boolean first = i == 0;
            boolean last = end == n;
            sb.append(ESC).append("_G");
            if (first) {
                sb.append("a=T,f=24,o=z,s=").append(pxW).append(",v=").append(pxH)
                        .append(",i=").append(cur).append(",q=2,C=1,c=").append(cols).append(",r=").append(rows).append(',');
            }
            sb.append("m=").append(last ? 0 : 1).append(';').append(b64, i, end).append(ST);
        }
        sb.append(deleteById(prev));   // only now drop the previous frame underneath — region was never blank
        append(out, sb);
    }

    /** Removes both buffered images (toggle-off / teardown); either id may be the one currently displayed. */
    public static void delete(Appendable out) {
        StringBuilder sb = new StringBuilder();
        for (int id : IDS) sb.append(deleteById(id));
        append(out, sb);
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

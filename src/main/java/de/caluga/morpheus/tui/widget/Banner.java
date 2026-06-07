package de.caluga.morpheus.tui.widget;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

/** Static "MORPHEUS" slant-figlet banner with a vertical Matrix-green gradient. */
public class Banner {

    /** Slant-figlet "MORPHEUS" (5 lines). */
    public static final String[] LINES = {
        "    __  _______  ____  ____  __  __________  _______",
        "   /  |/  / __ \\/ __ \\/ __ \\/ / / / ____/ / / / ___/",
        "  / /|_/ / / / / /_/ / /_/ / /_/ / __/ / / / /\\__ \\ ",
        " / /  / / /_/ / _, _/ ____/ __  / /___/ /_/ /___/ / ",
        "/_/  /_/\\____/_/ |_/_/   /_/ /_/_____/\\____//____/  "
    };

    private static final int TOP_R = 0,   TOP_G = 80,  TOP_B = 0;
    private static final int BOT_R = 140, BOT_G = 255, BOT_B = 140;

    private Banner() {}

    /** Matrix-green gradient color for a line (dark green top → bright green bottom). */
    public static TextColor.RGB lineColor(int lineIndex, int totalLines) {
        double t = totalLines <= 1 ? 0.0 : (double) lineIndex / (totalLines - 1);
        int r = (int) Math.round(TOP_R + (BOT_R - TOP_R) * t);
        int g = (int) Math.round(TOP_G + (BOT_G - TOP_G) * t);
        int b = (int) Math.round(TOP_B + (BOT_B - TOP_B) * t);
        return new TextColor.RGB(r, g, b);
    }

    /** Draws the banner at (x, y), each line colored by the gradient. */
    public static void draw(TextGraphics g, int x, int y) {
        for (int i = 0; i < LINES.length; i++) {
            g.setForegroundColor(lineColor(i, LINES.length));
            g.putString(x, y + i, LINES[i]);
        }
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
    }
}

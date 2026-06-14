package de.caluga.morpheus.tui.widget;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

import java.util.Arrays;

/** A grid of terminal cells, each a 2x4 Braille dot matrix (U+2800–28FF), giving
 *  2*cols x 4*rows subpixel resolution. Last writer wins for a cell's colour. */
public class BrailleCanvas {

    // Braille dot bit per (dx, dy):  dx=0 column then dx=1 column, dy=0..3 top to bottom.
    private static final int[][] DOT = {
            {0x01, 0x02, 0x04, 0x40},
            {0x08, 0x10, 0x20, 0x80},
    };

    private final int cols;
    private final int rows;
    private final int[] bits;
    private final TextColor[] colors;

    public BrailleCanvas(int cols, int rows) {
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);
        this.bits = new int[this.cols * this.rows];
        this.colors = new TextColor[this.cols * this.rows];
    }

    public int width() { return cols * 2; }
    public int height() { return rows * 4; }

    public void clear() {
        Arrays.fill(bits, 0);
        Arrays.fill(colors, null);
    }

    public void plot(int sx, int sy, TextColor color) {
        if (sx < 0 || sy < 0 || sx >= width() || sy >= height()) return;
        int idx = (sy / 4) * cols + (sx / 2);
        bits[idx] |= DOT[sx % 2][sy % 4];
        colors[idx] = color;
    }

    /** Bresenham over subpixels. */
    public void line(int x0, int y0, int x1, int y1, TextColor color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            plot(x0, y0, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    public void render(TextGraphics g, int originX, int originY) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                if (bits[idx] == 0) continue;
                g.setForegroundColor(colors[idx] != null ? colors[idx] : TextColor.ANSI.WHITE);
                g.putString(originX + col, originY + row, String.valueOf((char) (0x2800 + bits[idx])));
            }
        }
    }

    // --- test seams ---
    char glyphAt(int col, int row) { return (char) (0x2800 + bits[row * cols + col]); }
    TextColor colorAt(int col, int row) { return colors[row * cols + col]; }
}

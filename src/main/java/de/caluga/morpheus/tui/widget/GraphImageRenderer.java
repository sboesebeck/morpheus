package de.caluga.morpheus.tui.widget;

import com.googlecode.lanterna.TextColor;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** Renders the graph scene (nodes + in-flight shots, in pixel coordinates) to a PNG using headless
 *  Graphics2D: anti-aliased node circles with a halo, glowing shot dots on faint chords, drawString labels. */
public final class GraphImageRenderer {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    public record NodeView(double x, double y, String label, String host, boolean idle) {}

    public record ShotView(double x0, double y0, double x1, double y1, double progress, TextColor color) {}

    public byte[] render(int pxW, int pxH, List<NodeView> nodes, List<ShotView> shots) {
        int w = Math.max(1, pxW);
        int h = Math.max(1, pxH);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(10, 12, 18));
        g.fillRect(0, 0, w, h);

        // faint chords under in-flight shots
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(45, 70, 60));
        for (ShotView s : shots) {
            g.drawLine((int) s.x0(), (int) s.y0(), (int) s.x1(), (int) s.y1());
        }

        // shots: glowing dot at the interpolated position, white core
        for (ShotView s : shots) {
            double px = s.x0() + (s.x1() - s.x0()) * s.progress();
            double py = s.y0() + (s.y1() - s.y0()) * s.progress();
            Color c = awt(s.color());
            glow(g, px, py, 16, new Color(c.getRed(), c.getGreen(), c.getBlue(), 210));
            g.setColor(Color.WHITE);
            g.fillOval((int) px - 3, (int) py - 3, 6, 6);
        }

        // nodes + labels
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        for (NodeView nd : nodes) {
            Color core = nd.idle() ? new Color(95, 110, 95) : new Color(60, 230, 140);
            glow(g, nd.x(), nd.y(), 13, new Color(core.getRed(), core.getGreen(), core.getBlue(), 80));
            g.setColor(core);
            g.fillOval((int) nd.x() - 5, (int) nd.y() - 5, 10, 10);
            g.setColor(nd.idle() ? new Color(140, 150, 140) : new Color(190, 255, 215));
            g.drawString(nd.label(), (int) nd.x() + 9, (int) nd.y() + 4);
            if (nd.host() != null && !nd.host().isEmpty()) {
                g.setColor(new Color(110, 120, 110));
                g.drawString(nd.host(), (int) nd.x() + 9, (int) nd.y() + 18);
            }
        }

        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, "png", bos);
        } catch (IOException e) {
            return new byte[0];
        }
        return bos.toByteArray();
    }

    private static Color awt(TextColor t) {
        return new Color(t.getRed(), t.getGreen(), t.getBlue());
    }

    private static void glow(Graphics2D g, double x, double y, float r, Color inner) {
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(x, y), r,
                new float[]{0f, 1f},
                new Color[]{inner, new Color(inner.getRed(), inner.getGreen(), inner.getBlue(), 0)}));
        g.fillOval((int) (x - r), (int) (y - r), (int) (2 * r), (int) (2 * r));
    }
}

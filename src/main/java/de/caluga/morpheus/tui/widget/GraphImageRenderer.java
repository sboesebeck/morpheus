package de.caluga.morpheus.tui.widget;

import com.googlecode.lanterna.TextColor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.Deflater;

/** Renders the graph scene (nodes + in-flight shots, in pixel coordinates) using headless Graphics2D
 *  (anti-aliased node circles with a halo, glowing shot dots on faint chords, drawString labels) and returns
 *  the frame as zlib-compressed raw RGB — the Kitty f=24,o=z payload. This deliberately avoids ImageIO/PNG
 *  encoding, which measured 60–80 ms per frame and starved the single render+input thread; Deflater is an
 *  order of magnitude faster and the terminal skips PNG decoding too. */
public final class GraphImageRenderer {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    public record NodeView(double x, double y, String label, String host, boolean idle) {}

    public record ShotView(double x0, double y0, double x1, double y1, double progress, TextColor color) {}

    public record ChordView(double x0, double y0, double x1, double y1, double alpha) {}

    private static final Color CHORD = new Color(45, 70, 60);   // resting line / grey baseline of a pulse
    private static final Color TRAIL = new Color(60, 95, 80);   // fading afterglow of a recently used connection

    /** Renders the scene and returns it as zlib-compressed raw RGB bytes (w*h*3 RGB, deflated). */
    public byte[] render(int pxW, int pxH, List<NodeView> nodes, List<ShotView> shots, List<ChordView> trails, boolean pulse) {
        int w = Math.max(1, pxW);
        int h = Math.max(1, pxH);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(10, 12, 18));
        g.fillRect(0, 0, w, h);

        if (pulse) {
            // grey trails of recently used connections, fading out (drawn first, beneath the live pulses).
            // The fade holds brightness in the upper range (pow 0.7) so the line lingers visibly, not just a blink.
            g.setStroke(new BasicStroke(1.2f));
            for (ChordView c : trails) {
                int a = (int) (Math.pow(clamp01(c.alpha()), 0.4) * 200);
                g.setColor(new Color(TRAIL.getRed(), TRAIL.getGreen(), TRAIL.getBlue(), a));
                g.drawLine((int) c.x0(), (int) c.y0(), (int) c.x1(), (int) c.y1());
            }
            // each in-flight message pulses its whole chord: grey → topic colour → grey over its life
            for (ShotView s : shots) {
                double t = Math.sin(clamp01(s.progress()) * Math.PI);   // 0 at the ends, 1 at mid-flight
                g.setStroke(new BasicStroke((float) (0.8 + 1.0 * t)));
                g.setColor(lerp(CHORD, awt(s.color()), t));
                g.drawLine((int) s.x0(), (int) s.y0(), (int) s.x1(), (int) s.y1());
            }
        } else {
            // faint chords under in-flight shots
            g.setStroke(new BasicStroke(1.5f));
            g.setColor(CHORD);
            for (ShotView s : shots) {
                g.drawLine((int) s.x0(), (int) s.y0(), (int) s.x1(), (int) s.y1());
            }
            // shots: small glowing dot at the interpolated position, white core
            for (ShotView s : shots) {
                double px = s.x0() + (s.x1() - s.x0()) * s.progress();
                double py = s.y0() + (s.y1() - s.y0()) * s.progress();
                Color c = awt(s.color());
                glow(g, px, py, 9, new Color(c.getRed(), c.getGreen(), c.getBlue(), 210));
                g.setColor(Color.WHITE);
                g.fillOval((int) px - 2, (int) py - 2, 4, 4);
            }
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

        // Pack the opaque TYPE_INT_RGB pixels into raw RGB (3 bytes/pixel, row-major top-to-bottom) and zlib it.
        int[] px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        byte[] rgb = new byte[w * h * 3];
        int j = 0;
        for (int p : px) {
            rgb[j++] = (byte) (p >> 16);   // R
            rgb[j++] = (byte) (p >> 8);    // G
            rgb[j++] = (byte) p;           // B
        }
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(rgb);
        deflater.finish();
        ByteArrayOutputStream bos = new ByteArrayOutputStream(rgb.length / 2);
        byte[] buf = new byte[16384];
        while (!deflater.finished()) {
            bos.write(buf, 0, deflater.deflate(buf));
        }
        deflater.end();
        return bos.toByteArray();
    }

    private static Color awt(TextColor t) {
        return new Color(t.getRed(), t.getGreen(), t.getBlue());
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** Linear blend a→b by t∈[0,1]. */
    private static Color lerp(Color a, Color b, double t) {
        t = clamp01(t);
        return new Color(
                (int) (a.getRed()   + (b.getRed()   - a.getRed())   * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
    }

    private static void glow(Graphics2D g, double x, double y, float r, Color inner) {
        g.setPaint(new RadialGradientPaint(
                new Point2D.Double(x, y), r,
                new float[]{0f, 1f},
                new Color[]{inner, new Color(inner.getRed(), inner.getGreen(), inner.getBlue(), 0)}));
        g.fillOval((int) (x - r), (int) (y - r), (int) (2 * r), (int) (2 * r));
    }
}

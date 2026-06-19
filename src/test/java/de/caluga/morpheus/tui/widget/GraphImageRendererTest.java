package de.caluga.morpheus.tui.widget;

import com.googlecode.lanterna.TextColor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.*;

public class GraphImageRendererTest {

    /** Inflates a zlib stream and returns the raw byte count (must equal w*h*3 for an RGB frame). */
    private int inflatedLength(byte[] z) throws Exception {
        Inflater inf = new Inflater();
        inf.setInput(z);
        byte[] buf = new byte[64 * 1024];
        int total = 0;
        while (!inf.finished()) {
            int n = inf.inflate(buf);
            if (n == 0 && inf.needsInput()) break;
            total += n;
        }
        inf.end();
        return total;
    }

    @Test
    void rendersSceneToZlibRgbFrame() throws Exception {
        GraphImageRenderer r = new GraphImageRenderer();
        List<GraphImageRenderer.NodeView> nodes = List.of(
                new GraphImageRenderer.NodeView(60, 50, "hermes ↑5 ↓0", null, false),
                new GraphImageRenderer.NodeView(160, 80, "w1 ↑0 ↓5", "host-1", true));
        List<GraphImageRenderer.ShotView> shots = List.of(
                new GraphImageRenderer.ShotView(60, 50, 160, 80, 0.5, TextColor.ANSI.GREEN_BRIGHT),
                new GraphImageRenderer.ShotView(160, 80, 60, 50, 0.2, TextColor.ANSI.RED_BRIGHT));
        byte[] z = r.render(240, 140, nodes, shots, List.of(), false);
        assertTrue(z.length > 0);
        assertEquals((byte) 0x78, z[0], "zlib stream header (0x78)");
        assertEquals(240 * 140 * 3, inflatedLength(z), "inflates to w*h*3 raw RGB bytes");
    }

    @Test
    void pulseModeWithTrailsRendersToZlibRgbFrame() throws Exception {
        GraphImageRenderer r = new GraphImageRenderer();
        List<GraphImageRenderer.NodeView> nodes = List.of(
                new GraphImageRenderer.NodeView(60, 50, "hermes ↑5 ↓0", null, false));
        List<GraphImageRenderer.ShotView> shots = List.of(
                new GraphImageRenderer.ShotView(60, 50, 160, 80, 0.5, TextColor.ANSI.GREEN_BRIGHT),
                new GraphImageRenderer.ShotView(160, 80, 60, 50, 0.0, TextColor.ANSI.RED_BRIGHT));   // t=0 edge
        List<GraphImageRenderer.ChordView> trails = List.of(
                new GraphImageRenderer.ChordView(60, 50, 160, 80, 0.8),
                new GraphImageRenderer.ChordView(60, 50, 160, 80, 0.0));                             // fully faded edge
        byte[] z = r.render(240, 140, nodes, shots, trails, true);
        assertTrue(z.length > 0);
        assertEquals(240 * 140 * 3, inflatedLength(z), "inflates to w*h*3 raw RGB bytes");
    }

    @Test
    void emptySceneAndTinyCanvasStillProduceFrame() throws Exception {
        GraphImageRenderer r = new GraphImageRenderer();
        assertEquals(40 * 20 * 3, inflatedLength(r.render(40, 20, List.of(), List.of(), List.of(), false)));
        assertTrue(r.render(0, 0, List.of(), List.of(), List.of(), false).length > 0);   // guarded min size, no throw
        assertEquals(40 * 20 * 3, inflatedLength(r.render(40, 20, List.of(), List.of(), List.of(), true)));  // pulse, empty
    }
}

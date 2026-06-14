package de.caluga.morpheus.tui.widget;

import com.googlecode.lanterna.TextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GraphImageRendererTest {

    @Test
    void rendersSceneToNonEmptyPng() {
        GraphImageRenderer r = new GraphImageRenderer();
        List<GraphImageRenderer.NodeView> nodes = List.of(
                new GraphImageRenderer.NodeView(60, 50, "hermes ↑5 ↓0", null, false),
                new GraphImageRenderer.NodeView(160, 80, "w1 ↑0 ↓5", "host-1", true));
        List<GraphImageRenderer.ShotView> shots = List.of(
                new GraphImageRenderer.ShotView(60, 50, 160, 80, 0.5, TextColor.ANSI.GREEN_BRIGHT),
                new GraphImageRenderer.ShotView(160, 80, 60, 50, 0.2, TextColor.ANSI.RED_BRIGHT));
        byte[] png = r.render(240, 140, nodes, shots);
        assertTrue(png.length > 0);
        // PNG signature
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 'P', png[1]);
        assertEquals((byte) 'N', png[2]);
        assertEquals((byte) 'G', png[3]);
    }

    @Test
    void emptySceneAndTinyCanvasStillProducePng() {
        GraphImageRenderer r = new GraphImageRenderer();
        assertTrue(r.render(40, 20, List.of(), List.of()).length > 0);
        assertTrue(r.render(0, 0, List.of(), List.of()).length > 0);   // guarded min size, no throw
    }
}

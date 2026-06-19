package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import de.caluga.morpheus.core.NodeRegistry;
import de.caluga.morpheus.tui.Screen;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GraphScreenTest {

    @Test
    void escPopsAndPToggles() {
        GraphScreen s = new GraphScreen(new NodeRegistry("self"));
        assertEquals(Screen.Result.Kind.POP, s.onKey(new KeyStroke(KeyType.Escape)).kind());
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('p', false, false)).kind());
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('h', false, false)).kind());
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('b', false, false)).kind());   // pulse toggle
        assertEquals(Screen.Result.Kind.QUIT, s.onKey(new KeyStroke('q', false, false)).kind());
    }

    @Test
    void pulseModeRendersWithoutError() throws Exception {
        NodeRegistry r = new NodeRegistry("self");
        r.observeSend("hermes", "h1", 1000);
        r.observeRecv("worker2", "h3", 1000);
        GraphScreen s = new GraphScreen(r);
        s.onKey(new KeyStroke('b', false, false));   // switch to pulse (line-flash) mode
        renderOk(s, 120, 40);
        renderOk(s, 40, 12);
    }

    @Test
    void rendersSeededRegistryNarrowAndWide() throws Exception {
        NodeRegistry r = new NodeRegistry("self");
        r.observeSend("hermes", "h1", 1000);
        // an unset sender id shows up as a long uuid -> must shorten without throwing
        r.observeRecv("550e8400-e29b-41d4-a716-446655440000", "worker-03.prod.genios.de", 1000);
        r.markListener("550e8400-e29b-41d4-a716-446655440000", "worker-03.prod.genios.de", "order.created", 1000);
        r.observeRecv("worker2", "h3", 1000);
        GraphScreen s = new GraphScreen(r);
        renderOk(s, 120, 40);
        s.onKey(new KeyStroke('h', false, false));   // toggle host lines on
        renderOk(s, 120, 40);
        renderOk(s, 28, 8);     // below the minimum -> hint, must not throw
    }

    @Test
    void rendersEmptyRegistryWithoutError() throws Exception {
        renderOk(new GraphScreen(new NodeRegistry("self")), 100, 30);
    }

    @Test
    void gReturnsStayAndUnsupportedStaysBraille() throws Exception {
        NodeRegistry r = new NodeRegistry("self");
        r.observeSend("hermes", "h1", 1000);
        GraphScreen s = new GraphScreen(r);
        StringBuilder sink = new StringBuilder();
        s.gfxOut = sink;
        s.gfxSupportedCheck = () -> false;          // terminal does NOT support kitty graphics
        assertEquals(Screen.Result.Kind.STAY, s.onKey(new KeyStroke('g', false, false)).kind());
        renderOk(s, 120, 40);
        assertEquals(0, sink.length(), "unsupported terminal must emit no graphics escapes");
    }

    @Test
    void gOnSupportedTerminalEmitsImageAndToggleOffDeletes() throws Exception {
        NodeRegistry r = new NodeRegistry("self");
        r.observeSend("hermes", "h1", 1000);
        r.observeRecv("worker1", "h2", 1000);
        GraphScreen s = new GraphScreen(r);
        StringBuilder sink = new StringBuilder();
        s.gfxOut = sink;
        s.gfxSupportedCheck = () -> true;
        s.onKey(new KeyStroke('g', false, false));  // enable gfx
        renderOk(s, 120, 40);
        assertTrue(sink.toString().contains("a=T,f=24,o=z,"), "gfx mode emits a kitty image");

        sink.setLength(0);
        s.onKey(new KeyStroke('g', false, false));  // toggle off
        assertTrue(sink.toString().contains("\033_Ga=d,d=I,i=1"), "toggle-off deletes the image");
    }

    @Test
    void onCloseDeletesImageWhenGfxOn() {
        NodeRegistry r = new NodeRegistry("self");
        GraphScreen s = new GraphScreen(r);
        StringBuilder sink = new StringBuilder();
        s.gfxOut = sink;
        s.gfxSupportedCheck = () -> true;
        s.onKey(new KeyStroke('g', false, false));  // enable gfx
        s.onClose();
        assertTrue(sink.toString().contains("\033_Ga=d,d=I,i=1"), "onClose removes the image in gfx mode");
    }

    private void renderOk(GraphScreen s, int cols, int rows) throws Exception {
        DefaultVirtualTerminal vt = new DefaultVirtualTerminal(new com.googlecode.lanterna.TerminalSize(cols, rows));
        TerminalScreen ts = new TerminalScreen(vt);
        ts.startScreen();
        s.draw(ts.newTextGraphics());
        ts.stopScreen();
    }
}

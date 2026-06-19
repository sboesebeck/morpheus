package de.caluga.morpheus.tui.widget;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class KittyGraphicsTest {

    private Function<String, String> env(Map<String, String> m) {
        return m::get;
    }

    @Test
    void supportedDetectsKittyWeztermGhostty() {
        assertTrue(KittyGraphics.supported(env(Map.of("KITTY_WINDOW_ID", "1"))));
        assertTrue(KittyGraphics.supported(env(Map.of("TERM_PROGRAM", "WezTerm"))));
        assertTrue(KittyGraphics.supported(env(Map.of("GHOSTTY_RESOURCES_DIR", "/x"))));
        assertTrue(KittyGraphics.supported(env(Map.of("TERM", "xterm-kitty"))));
        assertTrue(KittyGraphics.supported(env(Map.of("TERM", "xterm-ghostty"))));
        assertFalse(KittyGraphics.supported(env(Map.of("TERM", "xterm-256color"))));
        assertFalse(KittyGraphics.supported(env(Map.of())));
    }

    @Test
    void emitWritesTransmitChunksThenDelete() {
        // ~6 KB payload → base64 ~8 KB → > one 4096 chunk, so chunking is exercised
        byte[] payload = new byte[6000];
        StringBuilder out = new StringBuilder();
        KittyGraphics.emit(payload, 320, 200, 80, 24, 2, 2, 0, out);   // frame 0 → cur id 1, prev id 2
        String s = out.toString();

        assertTrue(s.contains("\033[2;2H"), "positions the cursor at startRow;startCol");
        assertTrue(s.contains("a=T,f=24,o=z,s=320,v=200,i=1,q=2,C=1,c=80,r=24,"), "first chunk carries the raw-RGB transmit header");
        // chunking: more than one transmit escape, m=1 on the non-final chunk, m=0 on the last
        int transmits = s.split("\033_G", -1).length - 1;   // N transmit escapes + the trailing delete
        assertTrue(transmits >= 3, "at least two transmit chunks + the delete");
        assertTrue(s.contains("m=1;"), "non-final chunk marked m=1");
        assertTrue(s.contains("m=0;"), "final chunk marked m=0");
    }

    @Test
    void emitDisplaysNewFrameBeforeDeletingPreviousToAvoidFlicker() {
        byte[] payload = new byte[100];
        StringBuilder out = new StringBuilder();
        KittyGraphics.emit(payload, 320, 200, 80, 24, 2, 2, 0, out);   // frame 0 → display id 1, then delete id 2
        String s = out.toString();
        int transmit = s.indexOf("a=T");
        int delete = s.indexOf("a=d");
        assertTrue(transmit >= 0, "transmits the new frame");
        assertTrue(delete > transmit, "deletes the previous frame only AFTER displaying the new one (no blank gap)");
        assertTrue(s.contains("\033_Ga=d,d=I,i=2,q=2\033\\"), "drops the previous buffer (id 2) after frame 0");
    }

    @Test
    void emitAlternatesImageIdsAcrossFrames() {
        byte[] payload = new byte[100];
        StringBuilder f0 = new StringBuilder();
        StringBuilder f1 = new StringBuilder();
        KittyGraphics.emit(payload, 320, 200, 80, 24, 2, 2, 0, f0);    // even → display id 1, delete id 2
        KittyGraphics.emit(payload, 320, 200, 80, 24, 2, 2, 1, f1);    // odd  → display id 2, delete id 1
        assertTrue(f0.toString().contains("a=T,f=24,o=z,s=320,v=200,i=1,"), "even frame displays id 1");
        assertTrue(f0.toString().contains("\033_Ga=d,d=I,i=2,q=2\033\\"), "even frame deletes id 2");
        assertTrue(f1.toString().contains("a=T,f=24,o=z,s=320,v=200,i=2,"), "odd frame displays id 2");
        assertTrue(f1.toString().contains("\033_Ga=d,d=I,i=1,q=2\033\\"), "odd frame deletes id 1");
    }

    @Test
    void deleteRemovesBothBufferedImages() {
        StringBuilder out = new StringBuilder();
        KittyGraphics.delete(out);
        assertEquals("\033_Ga=d,d=I,i=1,q=2\033\\\033_Ga=d,d=I,i=2,q=2\033\\", out.toString());
    }
}

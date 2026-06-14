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
    void emitWritesReplaceIdiomAndChunks() {
        // ~6 KB payload → base64 ~8 KB → > one 4096 chunk, so chunking is exercised
        byte[] payload = new byte[6000];
        StringBuilder out = new StringBuilder();
        KittyGraphics.emit(payload, 80, 24, 2, 2, out);
        String s = out.toString();

        assertTrue(s.contains("\033[2;2H"), "positions the cursor at startRow;startCol");
        assertTrue(s.contains("\033_Ga=d,d=I,i=1,q=2\033\\"), "drops the previous frame by id");
        assertTrue(s.contains("a=T,f=100,i=1,q=2,C=1,c=80,r=24,"), "first chunk carries transmit+display header");
        // chunking: more than one transmit escape, m=1 on the non-final chunk, m=0 on the last
        int transmits = s.split("\033_G", -1).length - 1;   // delete + N transmit escapes
        assertTrue(transmits >= 3, "delete + at least two transmit chunks");
        assertTrue(s.contains("m=1;"), "non-final chunk marked m=1");
        assertTrue(s.contains("m=0;"), "final chunk marked m=0");
    }

    @Test
    void deleteWritesDeleteEscape() {
        StringBuilder out = new StringBuilder();
        KittyGraphics.delete(out);
        assertEquals("\033_Ga=d,d=I,i=1,q=2\033\\", out.toString());
    }
}

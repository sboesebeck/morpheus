package de.caluga.morpheus.tui.widget;

import com.googlecode.lanterna.TextColor;

/** Stable topic -> colour mapping so a topic always renders (and legends) in one colour. */
public final class TopicPalette {

    private static final TextColor[] PALETTE = {
            TextColor.ANSI.RED_BRIGHT, TextColor.ANSI.GREEN_BRIGHT, TextColor.ANSI.YELLOW_BRIGHT,
            TextColor.ANSI.BLUE_BRIGHT, TextColor.ANSI.MAGENTA_BRIGHT, TextColor.ANSI.CYAN_BRIGHT,
            TextColor.ANSI.WHITE_BRIGHT, TextColor.ANSI.YELLOW,
    };

    private TopicPalette() {}

    public static TextColor colorFor(String topic) {
        String t = topic == null ? "" : topic;
        return PALETTE[Math.floorMod(t.hashCode(), PALETTE.length)];
    }
}

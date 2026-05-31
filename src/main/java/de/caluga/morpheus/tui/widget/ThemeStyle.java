package de.caluga.morpheus.tui.widget;

import com.googlecode.lanterna.TextColor;

import java.util.Map;
import java.util.Set;

/** Maps theme markup tags to Lanterna colors and bold flags. */
public class ThemeStyle {
    private static final Map<String, TextColor> COLORS = Map.ofEntries(
            Map.entry("good", TextColor.ANSI.GREEN),
            Map.entry("error", TextColor.ANSI.RED),
            Map.entry("warning", TextColor.ANSI.YELLOW),
            Map.entry("header1", TextColor.ANSI.WHITE),
            Map.entry("header2", TextColor.ANSI.WHITE),
            Map.entry("c1", TextColor.ANSI.RED),
            Map.entry("c2", TextColor.ANSI.WHITE),
            Map.entry("c3", TextColor.ANSI.BLUE),
            Map.entry("rd", TextColor.ANSI.RED),
            Map.entry("gr", TextColor.ANSI.GREEN),
            Map.entry("y", TextColor.ANSI.YELLOW),
            Map.entry("b", TextColor.ANSI.BLUE),
            Map.entry("m", TextColor.ANSI.MAGENTA),
            Map.entry("c", TextColor.ANSI.CYAN),
            Map.entry("w", TextColor.ANSI.WHITE),
            Map.entry("r", TextColor.ANSI.DEFAULT));

    private static final Set<String> BOLD = Set.of("error", "header1", "c1", "bld");

    private ThemeStyle() {}

    public static TextColor color(String tag) {
        return COLORS.getOrDefault(tag, TextColor.ANSI.DEFAULT);
    }

    public static boolean bold(String tag) {
        return BOLD.contains(tag);
    }
}

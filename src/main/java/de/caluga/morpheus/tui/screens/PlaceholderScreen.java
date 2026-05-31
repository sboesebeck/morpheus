package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import de.caluga.morpheus.tui.Screen;

/** Temporary first screen until the launcher exists (Task 7). */
public class PlaceholderScreen implements Screen {
    @Override
    public void draw(TextGraphics g) {
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 1, "M O R P H E U S");
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, 3, "TUI scaffold running.");
        g.putString(2, 5, "[q] quit");
    }

    @Override
    public Result onKey(KeyStroke key) {
        if (key != null && key.getCharacter() != null && key.getCharacter() == 'q') {
            return Result.quit();
        }
        return Result.stay();
    }
}

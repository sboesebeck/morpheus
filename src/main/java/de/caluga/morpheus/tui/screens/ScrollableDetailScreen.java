package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import de.caluga.morpheus.tui.Screen;

/** Shared key handling for the read-only, line-scrollable detail screens:
 *  q quits, esc pops, arrow keys move the scroll offset. */
abstract class ScrollableDetailScreen implements Screen {

    protected int scroll = 0;

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) return Result.stay();
        if (key.getCharacter() != null && key.getCharacter() == 'q') return Result.quit();
        if (key.getKeyType() == KeyType.Escape) return Result.pop();
        if (key.getKeyType() == KeyType.ArrowDown) { scroll++; return Result.stay(); }
        if (key.getKeyType() == KeyType.ArrowUp) { if (scroll > 0) scroll--; return Result.stay(); }
        return Result.stay();
    }
}

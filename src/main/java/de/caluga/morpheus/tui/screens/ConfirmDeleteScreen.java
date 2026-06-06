package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.config.ConnectionStore;
import de.caluga.morpheus.tui.Screen;

/** Confirm dialog for deleting a connection. */
public class ConfirmDeleteScreen implements Screen {
    private final MorpheusContext ctx;
    private final String name;

    public ConfirmDeleteScreen(MorpheusContext ctx, String name) {
        this.ctx = ctx;
        this.name = name;
    }

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null || key.getCharacter() == null) return Result.stay();
        char c = Character.toLowerCase(key.getCharacter());
        if (c == 'j' || c == 'y') {
            try { new ConnectionStore(ctx.getConfig()).delete(name); } catch (Exception ignored) {}
            return Result.pop();
        }
        if (c == 'n') return Result.pop();
        return Result.stay();
    }

    @Override
    public void draw(TextGraphics g) {
        g.setForegroundColor(TextColor.ANSI.YELLOW);
        g.putString(2, 2, "Verbindung '" + name + "' löschen? [j/n]");
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
    }
}

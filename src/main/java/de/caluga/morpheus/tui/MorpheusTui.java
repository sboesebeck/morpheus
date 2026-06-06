package de.caluga.morpheus.tui;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen.RefreshType;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.tui.screens.PlaceholderScreen;

import java.io.IOException;

/** Lanterna runtime: opens a screen, runs the render/input loop, restores the terminal. */
public class MorpheusTui {

    private final com.googlecode.lanterna.screen.Screen lanternaScreen;
    private final MorpheusContext ctx;
    private final ScreenStack stack = new ScreenStack();

    public MorpheusTui(com.googlecode.lanterna.screen.Screen lanternaScreen, MorpheusContext ctx) {
        this.lanternaScreen = lanternaScreen;
        this.ctx = ctx;
    }

    /** Runs until the stack empties or a QUIT result. */
    public void run(Screen initial) throws IOException {
        stack.push(initial);
        lanternaScreen.startScreen();
        try {
            while (!stack.isEmpty()) {
                lanternaScreen.doResizeIfNecessary();
                lanternaScreen.clear();
                stack.top().draw(lanternaScreen.newTextGraphics());
                lanternaScreen.refresh(RefreshType.DELTA);

                KeyStroke key = lanternaScreen.pollInput();
                if (key == null) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    continue;
                }
                Screen.Result r = stack.top().onKey(key);
                if (r.kind() == Screen.Result.Kind.QUIT) {
                    break;
                }
                if (r.kind() == Screen.Result.Kind.POP || r.kind() == Screen.Result.Kind.REPLACE) {
                    stack.top().onClose();
                }
                stack.apply(r);
            }
        } finally {
            while (!stack.isEmpty()) { stack.pop().onClose(); }
            lanternaScreen.stopScreen();
        }
    }

    /** Production entry: real terminal, Logback silenced, placeholder screen (launcher in Task 7). */
    public static void launch(MorpheusContext ctx) {
        silenceConsoleLogging();
        try {
            com.googlecode.lanterna.screen.Screen screen =
                new DefaultTerminalFactory().createScreen();
            new MorpheusTui(screen, ctx).run(new de.caluga.morpheus.tui.screens.LauncherScreen(ctx));
        } catch (IOException e) {
            System.err.println("TUI failed: " + e.getMessage());
        }
    }

    /** Detach Logback's console appender so logs cannot corrupt the full-screen UI. */
    private static void silenceConsoleLogging() {
        try {
            org.slf4j.ILoggerFactory f = org.slf4j.LoggerFactory.getILoggerFactory();
            if (f instanceof ch.qos.logback.classic.LoggerContext lc) {
                lc.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME).detachAndStopAllAppenders();
            }
        } catch (Throwable ignored) {
            // not logback; nothing to silence
        }
    }
}

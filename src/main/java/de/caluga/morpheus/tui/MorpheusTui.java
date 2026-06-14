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
        java.io.PrintStream savedErr = System.err;
        // Stray library/driver stderr would corrupt the Lanterna buffer; swallow it for the session.
        System.setErr(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
        boolean forceComplete = false;
        try {
            while (!stack.isEmpty()) {
                lanternaScreen.doResizeIfNecessary();
                lanternaScreen.clear();
                stack.top().draw(lanternaScreen.newTextGraphics());
                lanternaScreen.refresh(forceComplete ? RefreshType.COMPLETE : RefreshType.DELTA);
                forceComplete = false;

                KeyStroke key = lanternaScreen.pollInput();
                if (key == null) {
                    Screen.Result tr = stack.top().tick();
                    if (tr.kind() == Screen.Result.Kind.QUIT) {
                        break;
                    }
                    if (tr.kind() == Screen.Result.Kind.STAY) {
                        try { Thread.sleep(stack.top().frameIntervalMs()); } catch (InterruptedException ignored) {}
                        continue;
                    }
                    if (tr.kind() == Screen.Result.Kind.POP || tr.kind() == Screen.Result.Kind.REPLACE) {
                        try { stack.top().onClose(); } catch (Throwable ignored) {}
                        forceComplete = true;
                    }
                    stack.apply(tr);
                    continue;
                }
                Screen.Result r = stack.top().onKey(key);
                if (r.kind() == Screen.Result.Kind.QUIT) {
                    break;
                }
                if (r.kind() == Screen.Result.Kind.POP || r.kind() == Screen.Result.Kind.REPLACE) {
                    try { stack.top().onClose(); } catch (Throwable ignored) {}
                    forceComplete = true; // fully repaint the revealed screen, leaving no remnants
                }
                stack.apply(r);
            }
        } finally {
            while (!stack.isEmpty()) {
                try { stack.pop().onClose(); } catch (Throwable ignored) {}
            }
            System.setErr(savedErr);
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

    /** CLI entry: open the TUI directly on one view, connecting async behind the spinner.
     *  The view is the only screen, so Esc / connect-failure empties the stack and the program exits. */
    public static void launchView(MorpheusContext ctx, String viewName) {
        silenceConsoleLogging();
        try {
            com.googlecode.lanterna.screen.Screen screen =
                new DefaultTerminalFactory().createScreen();
            new MorpheusTui(screen, ctx).run(
                new de.caluga.morpheus.tui.screens.ConnectingScreen(
                    ctx, viewName, de.caluga.morpheus.tui.screens.LauncherScreen::viewFor));
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

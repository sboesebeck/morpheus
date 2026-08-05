package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.config.ConfigurationManager;
import de.caluga.morpheus.tui.Screen;
import de.caluga.morpheus.utils.Failures;

import java.util.function.BiFunction;

/** Builds a connection off the render thread behind a spinner, then replaces itself with the view. */
public class ConnectingScreen implements Screen {

    /** Supplies a connected MorpheusContext (or throws). */
    public interface Connector {
        MorpheusContext connect() throws Exception;
    }

    private enum State { CONNECTING, READY, FAILED }

    private static final String[] SPINNER = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    private final String label;
    private final String viewName;
    private final BiFunction<String, MorpheusContext, Screen> viewFactory;

    private volatile State state = State.CONNECTING;
    private volatile MorpheusContext readyCtx;
    private volatile String error = "";
    private volatile boolean cancelled = false;
    private volatile boolean handedOff = false;
    private int frame = 0;

    /** Production: connect the named connection on its own fresh context. */
    public ConnectingScreen(String connectionName, String viewName,
                            BiFunction<String, MorpheusContext, Screen> viewFactory) {
        this(connectionName, viewName, () -> {
            ConfigurationManager cm = new ConfigurationManager();
            cm.setConnectionOverride(connectionName);
            MorpheusContext c = new MorpheusContext(cm);
            c.connect();
            return c;
        }, viewFactory);
    }

    /** CLI path: connect an already-built (but not yet connected) context on its own thread. */
    public ConnectingScreen(MorpheusContext unconnectedCtx, String viewName,
                            BiFunction<String, MorpheusContext, Screen> viewFactory) {
        this(labelFor(unconnectedCtx), viewName,
             () -> { unconnectedCtx.connect(); return unconnectedCtx; }, viewFactory);
    }

    private static String labelFor(MorpheusContext ctx) {
        String name = ctx.getConfig().getConnection();
        return (name == null || name.isEmpty()) ? "…" : name;
    }

    /** Test seam: inject the connect step and view factory. */
    ConnectingScreen(String label, String viewName, Connector connector,
                     BiFunction<String, MorpheusContext, Screen> viewFactory) {
        this.label = label;
        this.viewName = viewName;
        this.viewFactory = viewFactory;
        Thread t = new Thread(() -> {
            try {
                MorpheusContext c = connector.connect();
                if (cancelled) {
                    c.close();              // user bailed while connecting
                    return;
                }
                readyCtx = c;
                state = State.READY;
            } catch (Throwable e) {
                error = (e.getMessage() == null ? e.toString() : e.getMessage()) + Failures.causeDetail(e.getCause());
                state = State.FAILED;
            }
        }, "connecting");
        t.setDaemon(true);
        t.start();
    }

    /** Test seams (non-mutating). */
    boolean isFailed() { return state == State.FAILED; }
    boolean isReady()  { return state == State.READY; }
    String errorText() { return error; }

    @Override
    public Result tick() {
        if (state == State.READY && !handedOff) {
            Screen view;
            try {
                view = viewFactory.apply(viewName, readyCtx);
            } catch (Throwable e) {
                error = (e.getMessage() == null ? e.toString() : e.getMessage()) + Failures.causeDetail(e.getCause());
                state = State.FAILED;
                return Result.stay();
            }
            if (view == null) {
                state = State.FAILED;
                error = "Ansicht nicht verfügbar";
                return Result.stay();
            }
            handedOff = true;
            return Result.replace(view);
        }
        return Result.stay();
    }

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) return Result.stay();
        if (key.getCharacter() != null && key.getCharacter() == 'q') return Result.quit();
        if (key.getKeyType() == KeyType.Escape) return Result.pop();
        return Result.stay();
    }

    @Override
    public void onClose() {
        cancelled = true;
        if (readyCtx != null && !handedOff) readyCtx.close();
    }

    @Override
    public void draw(TextGraphics g) {
        int w = g.getSize().getColumns();
        int h = g.getSize().getRows();
        if (state == State.FAILED) {
            g.setForegroundColor(TextColor.ANSI.RED);
            String header = "✗ Verbindung fehlgeschlagen:";
            int maxWidth = Math.max(10, w - 4);
            java.util.List<String> lines = wrap(error, maxWidth);
            // Center the whole block vertically around h/2 instead of clipping the message at
            // the terminal edge (see review finding: a long cause chain - e.g. an SSL handshake
            // error - used to be cut off after a handful of characters, silently defeating the
            // whole point of attaching the real cause).
            int startY = Math.max(1, h / 2 - (lines.size() + 1) / 2);
            g.putString(center(w, header), startY, header);
            int y = startY + 1;
            int lastRow = h - 4; // leave room for a blank line + the "[esc] zurück" hint below,
                                  // so the "…" overflow marker itself never lands on the hint's row
            for (String line : lines) {
                if (y > lastRow) {
                    g.putString(center(w, "…"), y, "…");
                    break;
                }
                g.putString(center(w, line), y, line);
                y++;
            }
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
            String hint = "[esc] zurück";
            g.putString(center(w, hint), h - 2, hint);
            return;
        }
        String spin = SPINNER[frame % SPINNER.length];
        frame++;
        String line = spin + "  Verbinde mit " + label + " …";
        g.setForegroundColor(new TextColor.RGB(140, 255, 140));
        g.putString(center(w, line), h / 2, line);
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
    }

    /** Word-wraps text to at most {@code width} columns per line, breaking on spaces where
     * possible. Package-private so {@link LauncherScreen} can reuse it for the same reason:
     * long connect-failure messages must not be silently clipped at the terminal edge. */
    static java.util.List<String> wrap(String text, int width) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String remaining = text == null ? "" : text;
        width = Math.max(1, width); // guard against an infinite loop for a non-positive width
        while (remaining.length() > width) {
            int breakAt = remaining.lastIndexOf(' ', width);
            if (breakAt <= 0) {
                breakAt = width;
            }
            lines.add(remaining.substring(0, breakAt).stripTrailing());
            remaining = remaining.substring(breakAt).stripLeading();
        }
        lines.add(remaining);
        return lines;
    }

    private int center(int width, String s) {
        return Math.max(0, (width - s.length()) / 2);
    }
}

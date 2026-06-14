package de.caluga.morpheus.tui.screens;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import de.caluga.morpheus.cli.MorpheusContext;
import de.caluga.morpheus.core.FlowEvent;
import de.caluga.morpheus.core.FlowDeriver;
import de.caluga.morpheus.core.MessageFeed;
import de.caluga.morpheus.core.MessageTracker;
import de.caluga.morpheus.core.NodeRegistry;
import de.caluga.morpheus.core.NodeStatus;
import de.caluga.morpheus.core.StatusPinger;
import de.caluga.morpheus.tui.Screen;
import de.caluga.morpheus.tui.widget.BrailleCanvas;
import de.caluga.morpheus.tui.widget.TopicPalette;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Live message-flow graph: nodes on a ring, messages as topic-coloured shots on a Braille canvas. */
public class GraphScreen implements Screen {

    private static final int SHOT_CAP = 20;
    private static final long IDLE_MS = 15_000;

    private static final class Shot {
        final double x0, y0, x1, y1;
        final TextColor color;
        final String topic;
        double progress;
        final double speed;
        Shot(double x0, double y0, double x1, double y1, TextColor color, String topic, double speed) {
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
            this.color = color; this.topic = topic; this.speed = speed; this.progress = 0;
        }
    }

    private final MorpheusContext ownedCtx;   // null in the test seam
    private final MessageTracker tracker;     // null in the test seam
    private final FlowDeriver deriver;        // null in the test seam
    private final NodeRegistry registry;
    private final java.util.List<Shot> shots = new java.util.ArrayList<>();
    private volatile List<NodeStatus> pendingSeed;
    private volatile boolean seeded = false;
    private boolean paused = false;

    public GraphScreen(MorpheusContext ctx) {
        this.ownedCtx = ctx;
        String self = ctx.getMessaging() != null ? ctx.getMessaging().getSenderId() : "";
        this.registry = new NodeRegistry(self);
        this.tracker = new MessageTracker(200);
        this.deriver = new FlowDeriver(self, registry);
        if (ctx.getMorphium() != null) {
            MessageFeed feed = new MessageFeed(ctx.getMorphium(), ctx.getMessaging(), tracker, () -> {}, false);
            Thread ft = new Thread(() -> { try { feed.watch(); } catch (Throwable ignored) {} }, "graph-feed");
            ft.setDaemon(true);
            ft.start();
            Thread st = new Thread(() -> {
                try {
                    pendingSeed = new StatusPinger(ctx.getMessaging()).ping(5000);
                } catch (Throwable ignored) {
                } finally {
                    seeded = true;
                }
            }, "graph-seed");
            st.setDaemon(true);
            st.start();
        } else {
            seeded = true;
        }
    }

    /** Test seam: render a fixed registry, no backend. */
    GraphScreen(NodeRegistry registry) {
        this.ownedCtx = null;
        this.tracker = null;
        this.deriver = null;
        this.registry = registry;
        this.seeded = true;
    }

    @Override
    public void onClose() {
        if (ownedCtx != null) ownedCtx.close();
    }

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) return Result.stay();
        Character c = key.getCharacter();
        if (c != null && c == 'q') return Result.quit();
        if (c != null && c == 'p') { paused = !paused; return Result.stay(); }
        if (key.getKeyType() == KeyType.Escape) return Result.pop();
        return Result.stay();
    }

    @Override
    public void draw(TextGraphics g) {
        int width = g.getSize().getColumns();
        int height = g.getSize().getRows();
        long now = System.currentTimeMillis();

        if (width < 30 || height < 12) {
            g.setForegroundColor(TextColor.ANSI.DEFAULT);
            g.putString(2, 0, "Terminal zu klein für den Graph");
            return;
        }

        // apply a pending seed on the UI thread (registry is single-threaded)
        if (pendingSeed != null) {
            registry.seedFromStatus(pendingSeed, now);
            pendingSeed = null;
        }

        List<NodeRegistry.Node> nodes = registry.nodes();
        Map<String, double[]> pos = ringPositions(nodes, width, height);

        int canvasTop = 2;
        int canvasBottom = height - 3;

        // spawn shots from new flows
        if (deriver != null && tracker != null && !paused) {
            for (FlowEvent f : deriver.derive(tracker.getMessagesNewestFirst(), now)) {
                if (shots.size() >= SHOT_CAP) continue;           // saturation: skip animation (count already done)
                double[] from = pos.get(f.from());
                double[] to = pos.get(f.to());
                if (from == null || to == null) continue;         // a node not placed yet
                shots.add(new Shot(from[0], from[1], to[0], to[1], TopicPalette.colorFor(f.topic()), f.topic(), 0.06));
            }
        }

        // advance + draw shots on the canvas
        int cCols = Math.max(1, width - 2);
        int cRows = Math.max(1, canvasBottom - canvasTop);
        BrailleCanvas canvas = new BrailleCanvas(cCols, cRows);
        Map<String, TextColor> legend = new LinkedHashMap<>();
        java.util.Iterator<Shot> it = shots.iterator();
        while (it.hasNext()) {
            Shot s = it.next();
            if (!paused) s.progress += s.speed;
            if (s.progress >= 1.0) { it.remove(); continue; }
            canvas.line((int) s.x0, (int) s.y0, (int) s.x1, (int) s.y1, TextColor.ANSI.BLACK_BRIGHT);
            double px = s.x0 + (s.x1 - s.x0) * s.progress;
            double py = s.y0 + (s.y1 - s.y0) * s.progress;
            canvas.plot((int) px, (int) py, s.color);
            legend.putIfAbsent(s.topic, s.color);
        }
        canvas.render(g, 1, canvasTop);

        // node markers + labels
        for (NodeRegistry.Node n : nodes) {
            double[] p = pos.get(n.id);
            if (p == null) continue;
            int cx = 1 + (int) (p[0] / 2);
            int cy = Math.max(canvasTop, Math.min(canvasBottom - 1, canvasTop + (int) (p[1] / 4)));
            boolean idle = registry.isIdle(n.id, now, IDLE_MS);
            g.setForegroundColor(idle ? TextColor.ANSI.BLACK_BRIGHT : TextColor.ANSI.GREEN_BRIGHT);
            String label = (idle ? "○ " : "● ") + n.id + " ↑" + n.sendCount + " ↓" + n.recvCount;
            int lx = Math.max(2, Math.min(cx, width - 2 - Math.min(label.length(), width - 4)));
            g.putString(lx, cy, trunc(label, width - 2));
        }

        // title + stats
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 0, trunc("Message Flow" + (seeded ? "" : "   (Knoten werden entdeckt …)"), width - 3));
        String stats = registry.nodeCount() + " nodes · " + registry.topicCount() + " topics · " + shots.size() + " shots";
        g.setForegroundColor(TextColor.ANSI.YELLOW);
        g.putString(Math.max(2, width - stats.length() - 2), 0, stats);

        // legend (active topics, up to a few)
        int shown = 0;
        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, height - 2, "Topics: ");
        int lx = 10;
        for (Map.Entry<String, TextColor> e : legend.entrySet()) {
            if (shown++ >= 6 || lx > width - 6) break;
            g.setForegroundColor(e.getValue());
            String chip = "▮ " + trunc(e.getKey(), 16) + "  ";
            g.putString(lx, height - 2, chip);
            lx += chip.length();
        }

        g.setForegroundColor(TextColor.ANSI.DEFAULT);
        g.putString(2, height - 1, "[p] " + (paused ? "weiter" : "pause") + "   [esc] zurück   [q] quit");
    }

    /** Places nodes evenly on a ring in Braille subpixel coordinates. */
    private Map<String, double[]> ringPositions(List<NodeRegistry.Node> nodes, int width, int height) {
        Map<String, double[]> pos = new LinkedHashMap<>();
        int n = nodes.size();
        if (n == 0) return pos;
        double cw = (width - 2) * 2.0;
        double ch = (height - 5) * 4.0;
        double cx = cw / 2.0;
        double cy = ch / 2.0;
        double rad = Math.max(4, Math.min(cw, ch) / 2.0 * 0.75);
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n - Math.PI / 2;
            pos.put(nodes.get(i).id, new double[]{cx + rad * Math.cos(a), cy + rad * Math.sin(a)});
        }
        return pos;
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        if (max <= 1) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

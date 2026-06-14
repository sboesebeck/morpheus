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
        final Shot followUp;   // spawned when this shot arrives (e.g. the reply leg after the request)
        Shot(double x0, double y0, double x1, double y1, TextColor color, String topic, double speed, Shot followUp) {
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
            this.color = color; this.topic = topic; this.speed = speed; this.followUp = followUp; this.progress = 0;
        }
    }

    private final MorpheusContext ownedCtx;   // null in the test seam
    private final MessageTracker tracker;     // null in the test seam
    private final FlowDeriver deriver;        // null in the test seam
    private final NodeRegistry registry;
    private final java.util.List<Shot> shots = new java.util.ArrayList<>();
    private final Map<String, Double> radiusFrac = new java.util.HashMap<>();   // per-node eased ring-radius fraction (senders drift inward)
    private volatile List<NodeStatus> pendingSeed;
    private volatile boolean seeded = false;
    private boolean paused = false;
    private boolean showHosts = false;

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
        if (c != null && c == 'h') { showHosts = !showHosts; return Result.stay(); }
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

        int canvasTop = 1;
        int canvasBottom = height - 3;
        int cCols = Math.max(1, width - 2);
        int cRows = Math.max(1, canvasBottom - canvasTop);
        Map<String, double[]> pos = ringPositions(nodes, cCols * 2.0, cRows * 4.0);

        // spawn shots from new flows
        if (deriver != null && tracker != null && !paused) {
            for (FlowEvent f : deriver.derive(tracker.getMessagesNewestFirst(), now)) {
                if (shots.size() >= SHOT_CAP) continue;           // saturation: skip animation (count already done)
                double[] from = pos.get(f.from());
                double[] to = pos.get(f.to());
                if (from == null || to == null) continue;         // a node not placed yet
                if (f.kind() == FlowEvent.Kind.ANSWER) {
                    // request leg, then the reply leg back, sequentially
                    TextColor c = TopicPalette.colorFor(f.topic());
                    Shot reply = new Shot(to[0], to[1], from[0], from[1], c, f.topic(), 0.06, null);
                    shots.add(new Shot(from[0], from[1], to[0], to[1], c, f.topic(), 0.06, reply));
                } else if (f.kind() == FlowEvent.Kind.TIMEOUT) {
                    shots.add(new Shot(from[0], from[1], to[0], to[1], TextColor.ANSI.RED_BRIGHT, f.topic(), 0.06, null));
                } else {
                    shots.add(new Shot(from[0], from[1], to[0], to[1], TopicPalette.colorFor(f.topic()), f.topic(), 0.06, null));
                }
            }
        }

        // advance + draw shots on the canvas
        BrailleCanvas canvas = new BrailleCanvas(cCols, cRows);
        Map<String, TextColor> legend = new LinkedHashMap<>();
        java.util.List<Shot> followUps = new java.util.ArrayList<>();
        java.util.Iterator<Shot> it = shots.iterator();
        while (it.hasNext()) {
            Shot s = it.next();
            if (!paused) s.progress += s.speed;
            if (s.progress >= 1.0) {
                it.remove();
                if (s.followUp != null) followUps.add(s.followUp);   // start the reply leg on arrival
                continue;
            }
            canvas.line((int) s.x0, (int) s.y0, (int) s.x1, (int) s.y1, TextColor.ANSI.BLACK_BRIGHT);
            double px = s.x0 + (s.x1 - s.x0) * s.progress;
            double py = s.y0 + (s.y1 - s.y0) * s.progress;
            canvas.plot((int) px, (int) py, s.color);
            legend.putIfAbsent(s.topic, TopicPalette.colorFor(s.topic));
        }
        shots.addAll(followUps);
        canvas.render(g, 1, canvasTop);

        // node markers + labels (text fans outward: right-half nodes anchor their label to the left)
        int centerCol = 1 + cCols / 2;
        for (NodeRegistry.Node n : nodes) {
            double[] p = pos.get(n.id);
            if (p == null) continue;
            int cx = 1 + (int) (p[0] / 2);
            int cy = Math.max(canvasTop, Math.min(canvasBottom - 1, canvasTop + (int) (p[1] / 4)));
            boolean idle = registry.isIdle(n.id, now, IDLE_MS);
            String info = shortId(n.id) + " ↑" + n.sendCount + " ↓" + n.recvCount;
            String marker = idle ? "○" : "●";
            String label;
            int lx;
            if (cx > centerCol) {                       // right half: text left of the node, marker at the node
                label = info + " " + marker;
                lx = cx - (label.length() - 1);
            } else {                                    // left half: marker at the node, text to the right
                label = marker + " " + info;
                lx = cx;
            }
            lx = Math.max(2, Math.min(lx, width - 2));
            g.setForegroundColor(idle ? TextColor.ANSI.BLACK_BRIGHT : TextColor.ANSI.GREEN_BRIGHT);
            g.putString(lx, cy, trunc(label, Math.max(1, width - lx)));
            if (showHosts && n.host != null && !n.host.isEmpty() && cy + 1 <= canvasBottom - 1) {
                g.setForegroundColor(TextColor.ANSI.BLACK_BRIGHT);
                g.putString(lx, cy + 1, trunc(n.host, Math.max(1, width - lx)));
            }
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
        g.putString(2, height - 1, "[p] " + (paused ? "weiter" : "pause") + "   [h] hosts   [esc] zurück   [q] quit");
    }

    /** Places nodes on an ellipse filling the canvas; the angle is stable (by first-seen index),
     *  the radius eases inward with the node's "sender-ness" so request hubs drift to the middle and
     *  their shots stop crossing over rim nodes. Braille subpixel coordinates cw x ch. */
    private Map<String, double[]> ringPositions(List<NodeRegistry.Node> nodes, double cw, double ch) {
        Map<String, double[]> pos = new LinkedHashMap<>();
        int n = nodes.size();
        if (n == 0) return pos;
        double cx = cw / 2.0;
        double cy = ch / 2.0;
        double radX = Math.max(4, cw / 2.0 * 0.88);
        double radY = Math.max(4, ch / 2.0 * 0.88);
        for (int i = 0; i < n; i++) {
            NodeRegistry.Node nd = nodes.get(i);
            double a = 2 * Math.PI * i / n - Math.PI / 2;
            // sender-ness: 0 (pure receiver → rim) … 1 (pure sender → centre)
            double centrality = nd.sendCount / (double) (nd.sendCount + nd.recvCount + 1);
            double target = 1.0 - centrality * 0.7;     // a pure sender settles at ~0.3 of the radius
            double cur = radiusFrac.getOrDefault(nd.id, 1.0);
            cur += (target - cur) * 0.12;               // ease toward the target each frame
            radiusFrac.put(nd.id, cur);
            pos.put(nd.id, new double[]{cx + radX * cur * Math.cos(a), cy + radY * cur * Math.sin(a)});
        }
        radiusFrac.keySet().retainAll(pos.keySet());    // forget radii of nodes no longer present
        return pos;
    }

    /** Shortens an auto-generated sender id (UUID / object id) to head…tail; keeps short readable names as-is. */
    private String shortId(String id) {
        if (id == null) return "?";
        if (id.length() <= 16) return id;
        return id.substring(0, 6) + "…" + id.substring(id.length() - 4);
    }

    private String trunc(String s, int max) {
        if (s == null) return "";
        if (max <= 1) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

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
import de.caluga.morpheus.tui.widget.GraphImageRenderer;
import de.caluga.morpheus.tui.widget.KittyGraphics;
import de.caluga.morpheus.tui.widget.TopicPalette;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Live message-flow graph: nodes on a ring, messages as topic-coloured shots on a Braille canvas. */
public class GraphScreen implements Screen {

    private static final int SHOT_CAP = 20;
    private static final long IDLE_MS = 15_000;
    private static final int FRAME_MS = 33;          // ~30 fps for the cheap Braille renderer
    private static final int GFX_FRAME_MS = 33;      // ~30 fps for the Kitty image too (zlib encode is cheap; matches the shot-speed tuning)
    private static final long TRAIL_MS = 2500;       // how long a recently used connection lingers as a fading grey line
    private static final double SHOT_SPEED = 0.16;   // one leg ≈ 6 frames (~200ms) → round-trip ~450ms
    private static final int SPAWN_PER_FRAME = 3;     // spread bursts over frames so shots don't pulse 0↔cap
    private static final int MAX_PENDING = 300;       // flood guard for the spawn queue

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
    private final java.util.ArrayDeque<FlowEvent> pending = new java.util.ArrayDeque<>();   // flow events waiting to be spawned (rate-limited)
    private final Map<String, Double> radiusFrac = new java.util.HashMap<>();   // per-node eased ring-radius fraction (senders drift inward)
    private volatile List<NodeStatus> pendingSeed;
    private volatile boolean seeded = false;
    private boolean paused = false;
    private boolean showHosts = false;
    private boolean pulse = false;             // [b]: pulse whole chords (grey→colour→grey) instead of moving dots
    private boolean gfx = false;
    private int gfxFrame = 0;                  // double-buffer frame counter for the Kitty renderer
    private long lastGfxEmit = 0;              // wall-clock throttle: fixed 30 fps, decoupled from the loop rate
    private boolean showStats = false;         // [m]: overlay per-frame perf metrics (encode/transmit/size)
    private double lastEncodeMs = 0, lastTxMs = 0;
    private int pxWlast = 0, pxHlast = 0;
    private String gfxHint = null;
    private record Chord(String a, String b) {}                              // an unordered node pair
    private final Map<Chord, Long> chordSeen = new java.util.HashMap<>();    // pair → last time a message used it (for grey trails)
    private final GraphImageRenderer imageRenderer = new GraphImageRenderer();
    java.util.function.BooleanSupplier gfxSupportedCheck = KittyGraphics::supported;  // test seam
    Appendable gfxOut = System.out;                                                   // test seam

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
    public int frameIntervalMs() { return gfx ? GFX_FRAME_MS : FRAME_MS; }

    /** Normalises a directed flow into an unordered chord key. */
    private static Chord chordKey(String a, String b) {
        return a.compareTo(b) <= 0 ? new Chord(a, b) : new Chord(b, a);
    }

    @Override
    public void onClose() {
        if (gfx) KittyGraphics.delete(gfxOut);
        if (ownedCtx != null) ownedCtx.close();
    }

    @Override
    public Result onKey(KeyStroke key) {
        if (key == null) return Result.stay();
        Character c = key.getCharacter();
        if (c != null && c == 'q') return Result.quit();
        if (c != null && c == 'p') { paused = !paused; return Result.stay(); }
        if (c != null && c == 'h') { showHosts = !showHosts; return Result.stay(); }
        if (c != null && c == 'b') { pulse = !pulse; return Result.stay(); }
        if (c != null && c == 'm') { showStats = !showStats; return Result.stay(); }
        if (c != null && c == 'g') {
            if (gfx) {
                gfx = false;
                KittyGraphics.delete(gfxOut);
            } else if (gfxSupportedCheck.getAsBoolean()) {
                gfx = true;
                gfxHint = null;
                lastGfxEmit = 0;             // force an immediate first frame, bypassing the throttle
            } else {
                gfxHint = "Kitty-Grafik in diesem Terminal nicht erkannt";
            }
            return Result.stay();
        }
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
            // Queue all of this frame's flows; bursty change-stream batches must not all pop at once.
            for (FlowEvent f : deriver.derive(tracker.getMessagesNewestFirst(), now)) {
                pending.addLast(f);
            }
            while (pending.size() > MAX_PENDING) pending.pollFirst();   // flood guard: drop oldest
            // Spawn only a few per frame (saturation cap still applies) so the population stays steady.
            int budget = SPAWN_PER_FRAME;
            while (budget-- > 0 && shots.size() < SHOT_CAP && !pending.isEmpty()) {
                FlowEvent f = pending.pollFirst();
                double[] from = pos.get(f.from());
                double[] to = pos.get(f.to());
                if (from == null || to == null) continue;         // a node not placed yet; drop
                chordSeen.put(chordKey(f.from(), f.to()), now);   // refresh this connection's grey-trail timer
                if (f.kind() == FlowEvent.Kind.ANSWER) {
                    // request leg, then the reply leg back, sequentially
                    TextColor c = TopicPalette.colorFor(f.topic());
                    Shot reply = new Shot(to[0], to[1], from[0], from[1], c, f.topic(), SHOT_SPEED, null);
                    shots.add(new Shot(from[0], from[1], to[0], to[1], c, f.topic(), SHOT_SPEED, reply));
                } else if (f.kind() == FlowEvent.Kind.TIMEOUT) {
                    shots.add(new Shot(from[0], from[1], to[0], to[1], TextColor.ANSI.RED_BRIGHT, f.topic(), SHOT_SPEED, null));
                } else {
                    shots.add(new Shot(from[0], from[1], to[0], to[1], TopicPalette.colorFor(f.topic()), f.topic(), SHOT_SPEED, null));
                }
            }
        }

        // advance shots once (shared by both renderers); collect the live ones + the active-topic legend
        Map<String, TextColor> legend = new LinkedHashMap<>();
        List<Shot> live = advanceShots(legend);

        // fading grey trails of recently used connections (subpixel coords; each entry: {x0,y0,x1,y1,alpha})
        List<double[]> trails = new java.util.ArrayList<>();
        java.util.Iterator<Map.Entry<Chord, Long>> ti = chordSeen.entrySet().iterator();
        while (ti.hasNext()) {
            Map.Entry<Chord, Long> e = ti.next();
            long age = now - e.getValue();
            if (age >= TRAIL_MS) { ti.remove(); continue; }
            double[] pa = pos.get(e.getKey().a());
            double[] pb = pos.get(e.getKey().b());
            if (pa == null || pb == null) continue;            // a node not placed (yet/anymore)
            trails.add(new double[]{pa[0], pa[1], pb[0], pb[1], 1.0 - (double) age / TRAIL_MS});
        }

        if (gfx) {
            try {
                drawGfx(cCols, cRows, canvasTop, nodes, pos, live, trails);
            } catch (Throwable t) {
                gfx = false;                 // any failure → fall back to Braille
                KittyGraphics.delete(gfxOut);
            }
        }
        if (!gfx) {
            // Braille middle: render the live shots to the canvas, then node labels via Lanterna (unchanged)
            BrailleCanvas canvas = new BrailleCanvas(cCols, cRows);
            if (pulse) {
                // grey trails of recently used connections, fading out (drawn first, under the live pulses)
                for (double[] tr : trails) {
                    if (tr[4] > 0.15) canvas.line((int) tr[0], (int) tr[1], (int) tr[2], (int) tr[3], TextColor.ANSI.BLACK_BRIGHT);
                }
            }
            for (Shot s : live) {
                if (pulse) {
                    // whole chord lights up around mid-flight, then dims back to grey
                    double t = Math.sin(Math.max(0, Math.min(1, s.progress)) * Math.PI);
                    canvas.line((int) s.x0, (int) s.y0, (int) s.x1, (int) s.y1,
                            t > 0.35 ? s.color : TextColor.ANSI.BLACK_BRIGHT);
                } else {
                    canvas.line((int) s.x0, (int) s.y0, (int) s.x1, (int) s.y1, TextColor.ANSI.BLACK_BRIGHT);
                    double px = s.x0 + (s.x1 - s.x0) * s.progress;
                    double py = s.y0 + (s.y1 - s.y0) * s.progress;
                    canvas.plot((int) px, (int) py, s.color);
                }
            }
            canvas.render(g, 1, canvasTop);

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
                if (cx > centerCol) {
                    label = info + " " + marker;
                    lx = cx - (label.length() - 1);
                } else {
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
        }

        // title + stats
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 0, trunc("Message Flow" + (seeded ? "" : "   (Knoten werden entdeckt …)"), width - 3));
        String stats = registry.nodeCount() + " nodes · " + registry.topicCount() + " topics · " + shots.size() + " shots";
        if (gfx && showStats) stats += " · enc " + Math.round(lastEncodeMs) + "ms tx " + Math.round(lastTxMs)
                + "ms · " + pxWlast + "×" + pxHlast;
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
        g.putString(2, height - 1, trunc(
                "[p] " + (paused ? "weiter" : "pause") + "   [h] hosts   [g] grafik   [b] "
                        + (pulse ? "kugel" : "puls") + "   [m] stats   [esc] zurück   [q] quit", width - 3));
        if (gfxHint != null) {
            g.setForegroundColor(TextColor.ANSI.YELLOW);
            g.putString(2, height - 2, trunc(gfxHint, width - 3));
        }
    }

    /** Advances every shot once; removes finished ones (queuing their reply leg), records the active-topic
     *  legend, and returns the shots still in flight. Shared by the Braille and gfx renderers. */
    private List<Shot> advanceShots(Map<String, TextColor> legend) {
        List<Shot> liveShots = new java.util.ArrayList<>();
        List<Shot> followUps = new java.util.ArrayList<>();
        java.util.Iterator<Shot> it = shots.iterator();
        while (it.hasNext()) {
            Shot s = it.next();
            if (!paused) s.progress += s.speed;
            if (s.progress >= 1.0) {
                it.remove();
                if (s.followUp != null) followUps.add(s.followUp);
                continue;
            }
            legend.putIfAbsent(s.topic, TopicPalette.colorFor(s.topic));
            liveShots.add(s);
        }
        shots.addAll(followUps);
        return liveShots;
    }

    /** gfx middle: render nodes + live shots to a PNG and place it as a Kitty image owning the middle region.
     *  Coordinates are the Braille subpixel space (cCols*2 x cRows*4) scaled x4 into pixels. */
    private void drawGfx(int cCols, int cRows, int canvasTop, List<NodeRegistry.Node> nodes,
                         Map<String, double[]> pos, List<Shot> live, List<double[]> trails) {
        long now = System.currentTimeMillis();
        // Throttle the expensive PNG render + escape write to the gfx frame budget, independent of how often the
        // run loop calls draw(). Without this, every keystroke triggers a full image emit and input feels laggy.
        if (now - lastGfxEmit < GFX_FRAME_MS) return;
        lastGfxEmit = now;

        final double scale = 4.0;
        int pxW = (int) (cCols * 2 * scale);
        int pxH = (int) (cRows * 4 * scale);
        pxWlast = pxW;
        pxHlast = pxH;

        List<GraphImageRenderer.NodeView> nv = new java.util.ArrayList<>();
        for (NodeRegistry.Node n : nodes) {
            double[] p = pos.get(n.id);
            if (p == null) continue;
            boolean idle = registry.isIdle(n.id, now, IDLE_MS);
            String label = shortId(n.id) + " ↑" + n.sendCount + " ↓" + n.recvCount;
            String host = showHosts ? n.host : null;
            nv.add(new GraphImageRenderer.NodeView(p[0] * scale, p[1] * scale, label, host, idle));
        }
        List<GraphImageRenderer.ShotView> sv = new java.util.ArrayList<>();
        for (Shot s : live) {
            sv.add(new GraphImageRenderer.ShotView(
                    s.x0 * scale, s.y0 * scale, s.x1 * scale, s.y1 * scale, s.progress, s.color));
        }
        List<GraphImageRenderer.ChordView> tv = new java.util.ArrayList<>();
        for (double[] tr : trails) {
            tv.add(new GraphImageRenderer.ChordView(tr[0] * scale, tr[1] * scale, tr[2] * scale, tr[3] * scale, tr[4]));
        }
        long t0 = System.nanoTime();
        byte[] frame = imageRenderer.render(pxW, pxH, nv, sv, tv, pulse);
        long t1 = System.nanoTime();
        // the Braille canvas renders at Lanterna (col 1, row canvasTop); the image occupies the same region.
        KittyGraphics.emit(frame, pxW, pxH, cCols, cRows, canvasTop + 1, 2, gfxFrame++, gfxOut);
        long t2 = System.nanoTime();
        lastEncodeMs = (t1 - t0) / 1_000_000.0;
        lastTxMs = (t2 - t1) / 1_000_000.0;
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

package de.caluga.morpheus.core.uptime;

/** Polls the replica-set primary via an injected hello source and reports
 *  primary changes / outages of the status source itself into the UptimeTracker.
 *  During a failover the hello call typically fails — that window is reported
 *  once as RS_UNKNOWN (no spam) and closed with RS_RECOVERED. */
public class ReplicaSetWatcher {

    public interface HelloSource {
        /** @return current primary host ("host:port") or null if the backend does not expose one */
        String primary() throws Exception;
    }

    private final HelloSource source;
    private final UptimeTracker tracker;
    private final long pollMs;
    private volatile String primary;
    private volatile boolean unknown = false;
    private volatile boolean running = false;
    private Thread thread;
    private final Object lifecycle = new Object();

    public ReplicaSetWatcher(HelloSource source, UptimeTracker tracker, long pollMs) {
        this.source = source;
        this.tracker = tracker;
        this.pollMs = Math.max(100, pollMs);
    }

    public String primary() { return primary; }
    public boolean unknown() { return unknown; }

    void poll() {
        try {
            String p = source.primary();
            // If interrupted after the blocking call, discard result to avoid events after stop()
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (unknown) {
                unknown = false;
                tracker.recordEvent(UptimeTracker.Event.Type.RS_RECOVERED, "rs",
                        p == null ? "" : "primary: " + p);
            }
            if (p != null && !p.equals(primary)) {
                tracker.recordEvent(UptimeTracker.Event.Type.PRIMARY_CHANGE, "rs",
                        (primary == null ? "?" : primary) + " → " + p);
                primary = p;
            }
        } catch (Exception e) {
            // If interrupted, discard exception to avoid recording events after stop()
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (!unknown) {
                unknown = true;
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                tracker.recordEvent(UptimeTracker.Event.Type.RS_UNKNOWN, "rs", msg);
            }
        }
    }

    public void start() {
        synchronized (lifecycle) {
            if (running) return;
            running = true;
            thread = new Thread(() -> {
                while (running) {
                    poll();
                    try { Thread.sleep(pollMs); } catch (InterruptedException e) { return; }
                }
            }, "rs-watcher");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public void stop() {
        running = false;
        Thread t;
        synchronized (lifecycle) { t = thread; thread = null; }
        if (t != null) {
            t.interrupt();
            try { t.join(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
}

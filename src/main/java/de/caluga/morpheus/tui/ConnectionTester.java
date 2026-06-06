package de.caluga.morpheus.tui;

import java.util.concurrent.atomic.AtomicReference;

/** Runs a connection probe off-thread with a hard timeout; status() is non-blocking. */
public class ConnectionTester {

    public interface Probe {
        String run(String connectionName) throws Exception;
    }

    private final Probe probe;
    private final long timeoutMs;
    private final AtomicReference<String> status = new AtomicReference<>("");

    public ConnectionTester(Probe probe, long timeoutMs) {
        this.probe = probe;
        this.timeoutMs = timeoutMs;
    }

    public void start(String connectionName) {
        status.set("⟳ teste…");
        Thread worker = new Thread(() -> {
            try {
                status.set(probe.run(connectionName));
            } catch (InterruptedException e) {
                // watchdog interrupted us; it already set "✗ Timeout" — don't overwrite
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                status.set("✗ " + e.getMessage());
            }
        }, "conn-test");
        worker.setDaemon(true);
        worker.start();

        Thread watchdog = new Thread(() -> {
            try {
                worker.join(timeoutMs);
                if (worker.isAlive()) {
                    status.set("✗ Timeout");
                    worker.interrupt();
                }
            } catch (InterruptedException ignored) {
            }
        }, "conn-test-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    public String status() {
        return status.get();
    }
}

package de.caluga.morpheus.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe message statistics: totals, per-sender/per-host activity,
 * per-topic round-trip times. Pure data, no I/O.
 */
public class MessageStats {
    public record TopicRtt(String topic, long avgRtt, long count) {}
    public record NamedCount(String name, int count) {}

    private final AtomicLong totalMessages = new AtomicLong();
    private final AtomicLong totalAnswers = new AtomicLong();
    private final AtomicLong totalUpdates = new AtomicLong();
    private final AtomicLong totalTimeouts = new AtomicLong();
    private final long startTime = System.currentTimeMillis();

    private final ConcurrentHashMap<String, long[]> topicRtt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> hostCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> senderCounts = new ConcurrentHashMap<>();

    public void recordMessage(String sender, String host) {
        totalMessages.incrementAndGet();
        if (sender != null && !sender.isEmpty()) {
            senderCounts.computeIfAbsent(sender, k -> new AtomicInteger()).incrementAndGet();
        }
        if (host != null && !host.isEmpty()) {
            hostCounts.computeIfAbsent(host, k -> new AtomicInteger()).incrementAndGet();
        }
    }

    public void recordAnswer() { totalAnswers.incrementAndGet(); }
    public void recordUpdate() { totalUpdates.incrementAndGet(); }
    public void recordTimeouts(int n) { totalTimeouts.addAndGet(n); }

    public void recordRtt(String topic, long rttMs) {
        if (topic == null || topic.isEmpty()) return;
        topicRtt.compute(topic, (k, v) -> {
            if (v == null) return new long[] {rttMs, 1};
            v[0] += rttMs;
            v[1]++;
            return v;
        });
    }

    public long getTotalMessages() { return totalMessages.get(); }
    public long getTotalAnswers() { return totalAnswers.get(); }
    public long getTotalUpdates() { return totalUpdates.get(); }
    public long getTotalTimeouts() { return totalTimeouts.get(); }
    public long getStartTime() { return startTime; }

    public int getSenderCount(String sender) {
        AtomicInteger c = senderCounts.get(sender);
        return c == null ? 0 : c.get();
    }

    public int getHostCount(String host) {
        AtomicInteger c = hostCounts.get(host);
        return c == null ? 0 : c.get();
    }

    public long getAvgRtt(String topic) {
        long[] v = topicRtt.get(topic);
        return v == null || v[1] == 0 ? 0 : v[0] / v[1];
    }

    public long getRttCount(String topic) {
        long[] v = topicRtt.get(topic);
        return v == null ? 0 : v[1];
    }

    public List<TopicRtt> getSlowestTopics(int limit) {
        List<TopicRtt> all = new ArrayList<>();
        topicRtt.forEach((topic, v) -> all.add(new TopicRtt(topic, v[1] == 0 ? 0 : v[0] / v[1], v[1])));
        all.sort((a, b) -> Long.compare(b.avgRtt(), a.avgRtt()));
        return all.subList(0, Math.min(limit, all.size()));
    }

    public List<NamedCount> getTopHosts(int limit) { return top(hostCounts, limit); }
    public List<NamedCount> getTopSenders(int limit) { return top(senderCounts, limit); }

    private List<NamedCount> top(ConcurrentHashMap<String, AtomicInteger> map, int limit) {
        List<NamedCount> all = new ArrayList<>();
        map.forEach((name, c) -> all.add(new NamedCount(name, c.get())));
        all.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return all.subList(0, Math.min(limit, all.size()));
    }
}

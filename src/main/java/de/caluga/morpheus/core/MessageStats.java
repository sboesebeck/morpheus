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
    public record TopicAggregate(String topic, long messages, long answers, long avgRtt, long timeouts) {}

    private final AtomicLong totalMessages = new AtomicLong();
    private final AtomicLong totalAnswers = new AtomicLong();
    private final AtomicLong totalUpdates = new AtomicLong();
    private final AtomicLong totalTimeouts = new AtomicLong();
    private final long startTime = System.currentTimeMillis();

    private record RttAcc(AtomicLong sum, AtomicLong count) {
        void add(long v) { sum.addAndGet(v); count.incrementAndGet(); }
        long avg() { long c = count.get(); return c == 0 ? 0 : sum.get() / c; }
    }

    private final ConcurrentHashMap<String, RttAcc> topicRtt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> topicMessages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> topicTimeouts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> hostCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> senderCounts = new ConcurrentHashMap<>();

    /** Counts a message without attributing sender/host activity (used for answers). */
    public void recordMessage() {
        totalMessages.incrementAndGet();
    }

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
        topicRtt.computeIfAbsent(topic, k -> new RttAcc(new AtomicLong(), new AtomicLong())).add(rttMs);
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
        RttAcc acc = topicRtt.get(topic);
        return acc == null ? 0 : acc.avg();
    }

    public long getRttCount(String topic) {
        RttAcc acc = topicRtt.get(topic);
        return acc == null ? 0 : acc.count().get();
    }

    public void recordTopicMessage(String topic) {
        if (topic == null || topic.isEmpty()) return;
        topicMessages.computeIfAbsent(topic, k -> new AtomicLong()).incrementAndGet();
    }

    public void recordTopicTimeout(String topic) {
        if (topic == null || topic.isEmpty()) return;
        topicTimeouts.computeIfAbsent(topic, k -> new AtomicLong()).incrementAndGet();
    }

    public List<TopicAggregate> getTopicStats(int limit) {
        java.util.Set<String> topics = new java.util.HashSet<>();
        topics.addAll(topicMessages.keySet());
        topics.addAll(topicRtt.keySet());
        List<TopicAggregate> all = new ArrayList<>();
        for (String t : topics) {
            AtomicLong mc = topicMessages.get(t);
            AtomicLong tc = topicTimeouts.get(t);
            all.add(new TopicAggregate(t,
                    mc == null ? 0 : mc.get(),
                    getRttCount(t),
                    getAvgRtt(t),
                    tc == null ? 0 : tc.get()));
        }
        all.sort((a, b) -> Long.compare(b.messages(), a.messages()));
        return List.copyOf(all.subList(0, Math.min(limit, all.size())));
    }

    public List<TopicRtt> getSlowestTopics(int limit) {
        List<TopicRtt> all = new ArrayList<>();
        topicRtt.forEach((topic, acc) -> all.add(new TopicRtt(topic, acc.avg(), acc.count().get())));
        all.sort((a, b) -> Long.compare(b.avgRtt(), a.avgRtt()));
        return List.copyOf(all.subList(0, Math.min(limit, all.size())));
    }

    public List<NamedCount> getTopHosts(int limit) { return top(hostCounts, limit); }
    public List<NamedCount> getTopSenders(int limit) { return top(senderCounts, limit); }

    private List<NamedCount> top(ConcurrentHashMap<String, AtomicInteger> map, int limit) {
        List<NamedCount> all = new ArrayList<>();
        map.forEach((name, c) -> all.add(new NamedCount(name, c.get())));
        all.sort((a, b) -> Integer.compare(b.count(), a.count()));
        return List.copyOf(all.subList(0, Math.min(limit, all.size())));
    }
}

package de.caluga.morpheus.core;

/** One discrete message flow between two nodes, derived from the stream. */
public record FlowEvent(String from, String to, String topic, Kind kind, Long rttMs) {
    public enum Kind { DIRECT, EXCLUSIVE, BROADCAST, ANSWER, TIMEOUT }
}

package de.caluga.morpheus.config;

import java.util.List;

/** Value object describing one configured connection. hosts are host:port strings. */
public record ConnectionSpec(
        String name,
        List<String> hosts,
        String database,
        String authDb,
        String user,
        String password,
        String messagingImpl,
        String queueName,
        boolean proxyEnabled,
        String proxyHost,
        int proxyPort) {
}

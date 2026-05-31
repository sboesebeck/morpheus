package de.caluga.morpheus.config;

import de.caluga.morphium.MorphiumConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Pure connection persistence shared by the CLI and the TUI. */
public class ConnectionStore {
    private final ConfigurationManager cm;

    public ConnectionStore(ConfigurationManager cm) {
        this.cm = cm;
    }

    public Set<String> list() {
        return cm.getAvailableConnections();
    }

    public ConnectionSpec load(String name) {
        String prefix = "morphium." + name + ".";
        String hostSeed = cm.getProperty(prefix + "hostSeed", "[localhost:27017]");
        List<String> hosts = new ArrayList<>();
        for (String h : hostSeed.replaceAll("[\\[\\]\\\\]", "").split(",")) {
            String t = h.trim();
            if (!t.isEmpty()) hosts.add(t);
        }
        return new ConnectionSpec(
                name,
                hosts,
                cm.getProperty(prefix + "database", ""),
                cm.getProperty(prefix + "mongoAuthDb", "admin"),
                cm.getProperty(prefix + "mongoLogin", ""),
                cm.getProperty(prefix + "mongoPassword", ""),
                cm.getProperty(prefix + "messaging.implementation", "single"),
                cm.getProperty(prefix + "messaging.queueName", "msg"),
                cm.getProperty(prefix + "proxy.enabled", "false").equals("true"),
                cm.getProperty(prefix + "proxy.host", "127.0.0.1"),
                Integer.parseInt(cm.getProperty(prefix + "proxy.port", "5555")));
    }

    public void save(ConnectionSpec spec) throws Exception {
        String name = spec.name();
        String prefix = "morphium." + name + ".";
        boolean hasUser = spec.user() != null && !spec.user().isEmpty();

        MorphiumConfig cfg = new MorphiumConfig();
        for (String hostPort : spec.hosts()) {
            String[] parts = hostPort.trim().split(":");
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 27017;
            cfg.addHostToSeed(parts[0], port);
        }
        cfg.setDatabase(spec.database());
        cfg.setMongoAuthDb(spec.authDb());
        if (hasUser) {
            cfg.setMongoLogin(spec.user());
            if (spec.password() != null && !spec.password().isEmpty()) {
                cfg.setMongoPassword(spec.password());
            } else {
                String existing = cm.getProperty(prefix + "mongoPassword", "");
                if (!existing.isEmpty()) cfg.setMongoPassword(existing);
            }
        }

        Properties props = cfg.asProperties("morphium." + name);
        for (Object key : props.keySet()) {
            String k = key.toString();
            if (!hasUser && (k.contains(".login") || k.contains(".password"))) continue;
            cm.setProperty(k, props.getProperty(k));
        }

        cm.setProperty(prefix + "messaging.implementation", spec.messagingImpl());
        cm.setProperty(prefix + "messaging.queueName", spec.queueName());
        if (cm.getProperty(prefix + "messaging.processMultiple") == null)
            cm.setProperty(prefix + "messaging.processMultiple", "true");
        if (cm.getProperty(prefix + "messaging.multithreadded") == null)
            cm.setProperty(prefix + "messaging.multithreadded", "true");
        if (cm.getProperty(prefix + "messaging.windowSize") == null)
            cm.setProperty(prefix + "messaging.windowSize", "10");
        if (cm.getProperty(prefix + "messaging.pause") == null)
            cm.setProperty(prefix + "messaging.pause", "100");
        if (cm.getProperty(prefix + "messaging.senderId") == null)
            cm.setProperty(prefix + "messaging.senderId", UUID.randomUUID().toString());

        cm.setProperty(prefix + "proxy.enabled", String.valueOf(spec.proxyEnabled()));
        cm.setProperty(prefix + "proxy.host", spec.proxyHost());
        cm.setProperty(prefix + "proxy.port", String.valueOf(spec.proxyPort()));

        cm.save();
    }

    public void delete(String name) throws Exception {
        Set<Object> keys = cm.getProperties().keySet().stream()
                .filter(k -> k.toString().startsWith("morphium." + name + "."))
                .collect(Collectors.toSet());
        for (Object k : keys) cm.removeProperty(k.toString());
        cm.save();
    }
}

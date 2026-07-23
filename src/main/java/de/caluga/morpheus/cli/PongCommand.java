package de.caluga.morpheus.cli;

import de.caluga.morpheus.core.latency.PongResponder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.concurrent.Callable;

@Command(name = "pong", description = "Latency echo responder: answers pings on the latency topic until Ctrl-C.",
         mixinStandardHelpOptions = true)
public class PongCommand implements Callable<Integer> {

    @ParentCommand RootCommand parent;

    @Option(names = {"--topic"}, defaultValue = "morpheus_latency",
            description = "Topic to answer on (default: ${DEFAULT-VALUE}).")
    String topic;

    @Option(names = {"--name"}, description = "Responder name (default: hostname-pid).")
    String name;

    @Override
    public Integer call() throws Exception {
        MorpheusContext ctx = parent.context();
        ctx.printBanner();
        ctx.connect();
        // Ctrl-C sends SIGINT which kills the JVM without running MorpheusCli.run's
        // finally block (that only fires when execute() returns normally). Register
        // a shutdown hook so Morphium/messaging still get torn down cleanly.
        Runtime.getRuntime().addShutdownHook(new Thread(ctx::close, "pong-shutdown"));
        String n = name != null ? name : ManagementFactory.getRuntimeMXBean().getName().replace('@', '-');
        PongResponder pong = new PongResponder(n, System::currentTimeMillis);
        ctx.getMessaging().addListenerForTopic(topic, pong);
        ctx.pr("[c1]pong[r] '" + n + "' listening on topic [c3]" + topic + "[r] — Ctrl-C to stop");
        long lastCount = 0;
        while (true) {
            Thread.sleep(5000);
            long a = pong.answered();
            ctx.pr("answered: [good]" + a + "[r]  (+" + (a - lastCount) + " in 5s, "
                    + String.format(Locale.ROOT, "%.1f", (a - lastCount) / 5.0) + "/s)");
            lastCount = a;
        }
    }
}

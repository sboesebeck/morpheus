package de.caluga.morpheus.cli;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.Comparator;
import java.util.concurrent.Callable;

@Command(name = "send", description = "Send a message and optionally wait for answers.",
         mixinStandardHelpOptions = true)
public class SendCommand implements Callable<Integer> {

    @ParentCommand RootCommand parent;

    @Option(names = {"-t", "--topic"}, required = true, description = "Message topic (V5: name).")
    String topic;

    @Option(names = {"--msg"}, defaultValue = "", description = "Message text payload.")
    String msg;

    @Option(names = {"--value"}, defaultValue = "", description = "Message value payload.")
    String value;

    @Option(names = {"--ttl"}, description = "Message TTL in seconds (default: wait time).")
    Integer ttlSecs;

    @Option(names = {"-n", "--num-answers"}, defaultValue = "1",
            description = "Number of answers to wait for (default: ${DEFAULT-VALUE}).")
    int numAnswers;

    @Option(names = {"-w", "--wait"}, defaultValue = "30",
            description = "Seconds to wait for answers (default: ${DEFAULT-VALUE}).")
    int waitSecs;

    @Option(names = {"--no-wait"}, description = "Fire and forget - do not wait for answers.")
    boolean noWait;

    @Override
    public Integer call() throws Exception {
        MorpheusContext ctx = parent.context();
        ctx.printBanner();
        ctx.connect();

        int ttlMs = (ttlSecs != null ? ttlSecs : waitSecs) * 1000;
        Msg message = new Msg(topic, msg, value, ttlMs);
        message.setMsgId(new MorphiumId());

        if (noWait) {
            ctx.getMessaging().sendMessage(message);
            ctx.pr("   [c1]Message sent![r]");
            return 0;
        }

        ctx.pr("[c1]sending message[r]....(waiting " + waitSecs + "s for answers)\n");
        long sendTS = System.currentTimeMillis();
        var results = ctx.getMessaging().sendAndAwaitAnswers(message, numAnswers, waitSecs * 1000L);
        ctx.pr("[header1]got messages:[r]" + results.size());

        results.sort(Comparator.comparing(Msg::getSenderHost,
                Comparator.nullsLast(Comparator.naturalOrder())));

        boolean verbose = ctx.getConfig().isVerbose();
        if (!verbose) {
            ctx.pr("[c3]" + ctx.getColumn("Sender", 25) + " | " + ctx.getColumn("Host", 25)
                + " | " + ctx.getColumn("After ms", 8));
        }
        for (Msg r : results) {
            long after = r.getTimestamp() - sendTS;
            if (verbose) {
                ctx.pr("\n\n[header1]Answer:[r]");
                ctx.pr("Answer from: [c3]" + r.getSender() + "[r] on host [good]"
                    + r.getSenderHost() + "[r] after [warning]" + after + "ms[r]");
                if (r.getMapValue() != null) {
                    for (var k : r.getMapValue().entrySet()) {
                        ctx.pr("      [c3]" + ctx.getColumn(k.getKey(), 35) + "[r] | " + k.getValue());
                    }
                }
            } else {
                ctx.pr(ctx.getColumn(r.getSender(), 25) + " | "
                    + ctx.getColumn(r.getSenderHost(), 25) + " | "
                    + ctx.getColumn(after + "ms", 8));
            }
        }
        return results.isEmpty() ? 1 : 0;
    }
}

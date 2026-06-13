package de.caluga.morpheus.cli;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Command(name = "ping",
         description = "One-shot status ping of all messaging nodes (scriptable; supports --graphite).",
         mixinStandardHelpOptions = true)
public class PingCommand implements Callable<Integer> {

    public enum Level { PING, MESSAGING_ONLY, MORPHIUM_ONLY, ALL }

    @ParentCommand
    RootCommand parent;

    @Option(names = {"-w", "--wait"},
            description = "Seconds to wait for answers (default: ${DEFAULT-VALUE}).",
            defaultValue = "30")
    int wait;

    @Option(names = {"--level"},
            description = "Information level: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).",
            defaultValue = "ALL")
    Level level;

    @Option(names = {"--expect-answers"},
            description = "Maximum number of answers to collect (default: ${DEFAULT-VALUE}).",
            defaultValue = "10000")
    int expectAnswers;

    @Option(names = {"--filter-host"},
            description = "Regex pattern to filter answers by sender host.")
    Pattern filterHost;

    @Option(names = {"--filter-sender"},
            description = "Regex pattern to filter answers by sender name.")
    Pattern filterSender;

    @Option(names = {"--filter-path"},
            description = "Regex pattern: only show map entries whose path matches.")
    Pattern filterPath;

    @Option(names = {"--exclude-path"},
            description = "Regex pattern: hide map entries whose path matches.")
    Pattern excludePath;

    @Option(names = {"--keys"},
            description = "Comma-separated list of top-level map keys to display.",
            split = ",")
    List<String> keys = new ArrayList<>();

    @Option(names = {"--graphite"},
            description = "Graphite endpoint in the form host[:port] (default port 8125).")
    String graphite;

    // Graphite TCP socket state
    private Socket graphiteSocket;
    private PrintWriter graphiteOut;
    private String graphiteHost;
    private int graphitePort;

    @Override
    public Integer call() throws Exception {
        MorpheusContext ctx = parent.context();
        ctx.printBanner();
        ctx.connect();

        // --- graphite setup ---
        if (graphite != null) {
            graphiteHost = graphite;
            graphitePort = 8125;
            if (graphite.contains(":")) {
                String[] parts = graphite.split(":", 2);
                graphiteHost = parts[0];
                graphitePort = Integer.parseInt(parts[1]);
            }
            ctx.pr("Preparing Socket to graphite...");
            try {
                graphiteSocket = new Socket(graphiteHost, graphitePort);
                graphiteOut = new PrintWriter(new OutputStreamWriter(graphiteSocket.getOutputStream()));
            } catch (Exception e) {
                ctx.pr("[rd]FAILED[r]");
                if (ctx.getConfig().isVerbose()) {
                    e.printStackTrace();
                }
                return 1;
            }
        }

        // --- verbose is the global -v flag ---
        boolean verbose = ctx.getConfig().isVerbose();

        // Non-verbose: downgrade level to PING (no need for more info)
        if (!verbose) {
            if (level != Level.PING) {
                ctx.pr("[rd]setting level to PING - no need to get more info[r]");
                level = Level.PING;
            }
        }

        ctx.pr("[c1]sending status ping[r]....(waiting " + wait + "s for answers)\n");
        var msg = new Msg(ctx.getMessaging().getStatusInfoListenerName(), "ALL", level.name(), wait * 1000);
        msg.setMsgId(new MorphiumId());
        msg.setProcessedBy(new ArrayList<>());
        final int timeout = wait;
        final int exp = expectAnswers;

        List<Msg> answers = new ArrayList<>();
        AtomicBoolean gotResults = new AtomicBoolean(false);
        new Thread(() -> {
            ctx.pr("[c1]sending...[r]");
            var results = ctx.getMessaging().sendAndAwaitAnswers(msg, exp, timeout * 1000);
            for (var m : results) {
                answers.add(m);
            }
            gotResults.set(true);
            ctx.pr("[c2]got results...[r]: " + answers.size());
        }).start();

        int counter = 0;
        long start = System.currentTimeMillis();
        int filteredHosts = 0;
        int filteredOutput = 0;
        ctx.pr("Waiting max. " + timeout + "s for " + expectAnswers);

        while (!gotResults.get()) {
            counter++;
            Thread.sleep(1000);
            if (gotResults.get()) break;
            if (timeout > 0 && System.currentTimeMillis() - start > (timeout + 2) * 1000L) {
                break;
            }
            ctx.pr("Waiting... " + counter + "s");
        }

        long sendTS = start;
        ctx.pr("[header1]got messages:[r]" + answers.size());

        // Defensive null-safe sort (deviation: original NPEs when getSenderHost() == null)
        answers.sort(Comparator.comparing(Msg::getSenderHost,
                Comparator.nullsLast(Comparator.naturalOrder())));

        if (!verbose) {
            ctx.pr("[c3]" + ctx.getColumn("Sender", 25) + " | "
                    + ctx.getColumn("Host", 25) + " | "
                    + ctx.getColumn("After ms", 8));
        }

        for (Msg r : answers) {
            if (filterHost != null && !filterHost.matcher(r.getSenderHost()).matches()) {
                filteredHosts++;
                continue;
            }
            if (filterSender != null && !filterSender.matcher(r.getSender()).matches()) {
                filteredHosts++;
                continue;
            }

            if (verbose) {
                ctx.pr("\n\n[header1]Anwser:[r]");
                ctx.pr("Answer from: [c3]" + r.getSender() + "[r] on host [good]"
                        + r.getSenderHost() + "[r] after [warning]"
                        + (r.getTimestamp() - sendTS) + "ms[r]");
                sendToGraphite("answer.sender." + r.getSender(), r.getTimestamp() - sendTS);
                sendToGraphite("answer.host." + r.getSenderHost(), r.getTimestamp() - sendTS);

                if (r.getMapValue() != null) {
                    filteredOutput += printMap(ctx, r.getMapValue(), "", keys, filterPath, excludePath, r.getSender());
                }
            } else {
                long after = (r.getTimestamp() - sendTS);
                ctx.pr(ctx.getColumn(r.getSender(), 25) + " | "
                        + ctx.getColumn(r.getSenderHost(), 25) + " | " + after + "ms");
                sendToGraphite("answer.sender." + r.getSender(), after);
                sendToGraphite("answer.host." + r.getSenderHost(), after);
            }
        }

        ctx.pr("\n");
        ctx.pr("processed messages: " + answers.size() + "  Filtered hosts: " + filteredHosts
                + "    filtered Output: " + filteredOutput, 2);

        if (graphiteSocket != null) {
            graphiteOut.flush();
            graphiteSocket.close();
        }

        return 0;
    }

    private void sendToGraphite(String key, Object value) {
        if (graphiteSocket != null) {
            key = key.replaceAll(":", ".");
            if (key.startsWith(".")) {
                key = key.substring(1);
            }
            System.out.println("sending to graphite: '" + key + ":" + value + "|g'");
            try {
                graphiteOut.println(key + " " + value + " " + (System.currentTimeMillis() / 1000));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private int printMap(MorpheusContext ctx, Map<String, Object> map, String path,
                         List<String> keys, Pattern pathPattern, Pattern notFilterPath, String sender) {
        int filtered = 0;

        for (Map.Entry<String, Object> k : map.entrySet()) {
            if (keys != null && !keys.isEmpty() && !keys.contains(k.getKey())) {
                filtered++;
                continue;
            }

            if (k.getKey().equals("message_listeners_by_name")) {
                if (pathPattern != null && !pathPattern.matcher(path + ".registered Listeners:").matches()) {
                    filtered++;
                    continue;
                }
                if (notFilterPath != null && notFilterPath.matcher(path + ".registered Listeners:").matches()) {
                    filtered++;
                    continue;
                }
                ctx.pr("[c3]" + path + ".registered Listeners:[r]");
                String[] lst = k.getValue().toString().replaceAll("[{}]+", "").split(",");
                for (String l : lst) {
                    ctx.pr("[good]" + l.substring(0, l.indexOf("=")) + "[r]");
                }
            } else if (k.getValue() instanceof List) {
                if (((List<?>) k.getValue()).isEmpty()) continue;

                if (pathPattern != null && !pathPattern.matcher(path + "." + k.getKey()).matches()) {
                    filtered++;
                    continue;
                }
                if (notFilterPath != null && notFilterPath.matcher(path + "." + k.getKey()).matches()) {
                    filtered++;
                    continue;
                }
                ctx.pr("[good]" + path + "." + k.getKey() + "[r]");
                for (Object l : (List<?>) k.getValue()) {
                    if (l instanceof Map) {
                        filtered += printMap(ctx, (Map<String, Object>) l, path + "." + k.getKey(),
                                keys, pathPattern, notFilterPath, sender);
                    } else {
                        ctx.pr("[good]" + l.toString() + "[r]");
                        sendToGraphite(sender + path + "." + k.getKey(), l.toString());
                    }
                }
            } else if (k.getValue() instanceof Map) {
                if (pathPattern != null && !pathPattern.matcher(path + "." + k.getKey()).matches()) {
                    filtered++;
                    continue;
                }
                if (notFilterPath != null && notFilterPath.matcher(path + "." + k.getKey()).matches()) {
                    filtered++;
                    continue;
                }
                ctx.pr("[good]" + path + "." + k.getKey() + "[r]");
                filtered += printMap(ctx, (Map<String, Object>) k.getValue(), path + "." + k.getKey(),
                        keys, pathPattern, notFilterPath, sender);
            } else {
                if (pathPattern != null && !pathPattern.matcher(path + "." + k.getKey()).matches()) {
                    filtered++;
                    continue;
                }
                if (notFilterPath != null && notFilterPath.matcher(path + "." + k.getKey()).matches()) {
                    filtered++;
                    continue;
                }
                ctx.pr("[c3]" + path + "." + k.getKey() + "[r] | " + k.getValue());
                sendToGraphite(sender + path + "." + k.getKey(), k.getValue());
            }
        }

        return filtered;
    }
}

package de.caluga.morpheus.cli;

import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Command(name = "ping",
         description = "One-shot status ping of all messaging nodes (scriptable).",
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

    @Option(names = {"--json"},
            description = "Print answers as JSON (machine-readable) instead of themed output; "
                    + "for automated evaluation, e.g. feeding a monitoring backend.")
    boolean json;

    @Override
    public Integer call() throws Exception {
        MorpheusContext ctx = parent.context();
        if (!json) {
            ctx.printBanner();
        }
        ctx.connect();

        // --- verbose is the global -v flag ---
        boolean verbose = ctx.getConfig().isVerbose();

        // Non-verbose: downgrade level to PING (no need for more info).
        // Skipped in --json mode: the level is a machine-readable contract, not a display concern.
        if (!json && !verbose) {
            if (level != Level.PING) {
                ctx.pr("[rd]setting level to PING - no need to get more info[r]");
                level = Level.PING;
            }
        }

        if (!json) {
            ctx.pr("[c1]sending status ping[r]....(waiting " + wait + "s for answers)\n");
        }
        var msg = new Msg(ctx.getMessaging().getStatusInfoListenerName(), "ALL", level.name(), wait * 1000);
        msg.setMsgId(new MorphiumId());
        msg.setProcessedBy(new ArrayList<>());
        final int timeout = wait;
        final int exp = expectAnswers;

        List<Msg> answers = new ArrayList<>();
        AtomicBoolean gotResults = new AtomicBoolean(false);
        new Thread(() -> {
            if (!json) ctx.pr("[c1]sending...[r]");
            var results = ctx.getMessaging().sendAndAwaitAnswers(msg, exp, timeout * 1000);
            for (var m : results) {
                answers.add(m);
            }
            gotResults.set(true);
            if (!json) ctx.pr("[c2]got results...[r]: " + answers.size());
        }).start();

        int counter = 0;
        long start = System.currentTimeMillis();
        int filteredHosts = 0;
        int filteredOutput = 0;
        if (!json) ctx.pr("Waiting max. " + timeout + "s for " + expectAnswers);

        while (!gotResults.get()) {
            counter++;
            Thread.sleep(1000);
            if (gotResults.get()) break;
            if (timeout > 0 && System.currentTimeMillis() - start > (timeout + 2) * 1000L) {
                break;
            }
            if (!json) ctx.pr("Waiting... " + counter + "s");
        }

        long sendTS = start;

        if (json) {
            List<Msg> filtered = new ArrayList<>();
            for (Msg r : answers) {
                if (filterHost != null && !filterHost.matcher(r.getSenderHost()).matches()) continue;
                if (filterSender != null && !filterSender.matcher(r.getSender()).matches()) continue;
                filtered.add(r);
            }
            filtered.sort(Comparator.comparing(Msg::getSenderHost, Comparator.nullsLast(Comparator.naturalOrder())));
            System.out.println(reportJson(sendTS, wait, level, expectAnswers, filtered));
            return filtered.isEmpty() ? 2 : 0;
        }

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

                if (r.getMapValue() != null) {
                    filteredOutput += printMap(ctx, r.getMapValue(), "", keys, filterPath, excludePath);
                }
            } else {
                long after = (r.getTimestamp() - sendTS);
                ctx.pr(ctx.getColumn(r.getSender(), 25) + " | "
                        + ctx.getColumn(r.getSenderHost(), 25) + " | " + after + "ms");
            }
        }

        ctx.pr("\n");
        ctx.pr("processed messages: " + answers.size() + "  Filtered hosts: " + filteredHosts
                + "    filtered Output: " + filteredOutput, 2);

        return 0;
    }

    @SuppressWarnings("unchecked")
    private int printMap(MorpheusContext ctx, Map<String, Object> map, String path,
                         List<String> keys, Pattern pathPattern, Pattern notFilterPath) {
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
                                keys, pathPattern, notFilterPath);
                    } else {
                        ctx.pr("[good]" + l.toString() + "[r]");
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
                        keys, pathPattern, notFilterPath);
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
            }
        }

        return filtered;
    }

    /** Builds the --json output: one object with the ping parameters and one entry per answer.
     *  Payload is omitted (null) for {@link Level#PING} - pure ping/pong carries no data. */
    static String reportJson(long sentAt, int waitSec, Level level, int expectAnswers, List<Msg> answers) {
        boolean includePayload = level != Level.PING;
        StringBuilder b = new StringBuilder();
        b.append("{\"sentAt\":").append(sentAt)
         .append(",\"waitSec\":").append(waitSec)
         .append(",\"level\":\"").append(level.name()).append('"')
         .append(",\"expectAnswers\":").append(expectAnswers)
         .append(",\"answerCount\":").append(answers.size())
         .append(",\"answers\":[");
        for (int i = 0; i < answers.size(); i++) {
            if (i > 0) b.append(',');
            Msg m = answers.get(i);
            b.append("{\"sender\":");
            writeJsonString(b, m.getSender());
            b.append(",\"host\":");
            writeJsonString(b, m.getSenderHost());
            b.append(",\"rttMs\":").append(Math.max(0, m.getTimestamp() - sentAt));
            b.append(",\"payload\":");
            if (includePayload) {
                writeJsonValue(b, m.getMapValue());
            } else {
                b.append("null");
            }
            b.append('}');
        }
        return b.append("]}").toString();
    }

    private static void writeJsonValue(StringBuilder b, Object v) {
        if (v == null) {
            b.append("null");
        } else if (v instanceof String s) {
            writeJsonString(b, s);
        } else if (v instanceof Boolean || v instanceof Number) {
            b.append(v);
        } else if (v instanceof Map<?, ?> map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) b.append(',');
                first = false;
                writeJsonString(b, String.valueOf(e.getKey()));
                b.append(':');
                writeJsonValue(b, e.getValue());
            }
            b.append('}');
        } else if (v instanceof List<?> list) {
            b.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) b.append(',');
                writeJsonValue(b, list.get(i));
            }
            b.append(']');
        } else {
            writeJsonString(b, v.toString());
        }
    }

    private static void writeJsonString(StringBuilder b, String s) {
        if (s == null) {
            b.append("null");
            return;
        }
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        b.append('"');
    }
}

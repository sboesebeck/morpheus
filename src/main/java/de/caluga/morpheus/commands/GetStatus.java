package de.caluga.morpheus.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.json.simple.parser.JSONParser;
import de.caluga.morpheus.ICommand;
import de.caluga.morpheus.Morpheus;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.MessageListener;
import de.caluga.morphium.messaging.Messaging;
import de.caluga.morphium.messaging.Msg;

public class GetStatus implements ICommand, MessageListener {
    public final static String NAME = "get_status";
    public final static String DESCRIPTION =
        "getting status of all connected nodes, params wait=secs, verbose=true/false, expect_answers=NUM, filter_host=PATTERN, level=[PING,MESSAGING_ONLY,MORPHIUM_ONLY|ALL], filter_sender=PATTERN, keys=LIST_OF_KEYS, filter_path=PATTERN, not_filter_path=PATTERN";

    private MorphiumId sentMessageId;
    private List<Msg> answers = new ArrayList<>();

    @Override
    public void execute(Morpheus morpheus, Map<String, String> args) throws Exception {
        int sl = 30;
        Pattern filterHost = null;
        Pattern filterPath = null;
        Pattern notFilterPath = null;
        Pattern filterSender = null;
        String level = "ALL";
        Integer expectAnswers = 10000;
        List<String> keys = new ArrayList<>();

        if (args.containsKey("expect_answers")) {
            expectAnswers = Integer.valueOf(args.get("expect_answers"));
        }

        if (args.containsKey("level")) {
            level = args.get("level");
        }

        if (args.containsKey("keys")) {
            keys.addAll(Arrays.asList(args.get("keys").replaceAll(" ", "").split(",")));
        }

        if (args.containsKey("wait")) {
            sl = Integer.parseInt(args.get("wait"));
        }

        if (args.containsKey("filter_sender")) {
            filterSender = Pattern.compile(args.get("filter_sender"));
        }

        if (args.containsKey("filter_host")) {
            filterHost = Pattern.compile(args.get("filter_host"));
        }

        if (args.containsKey("filter_path")) {
            filterPath = Pattern.compile(args.get("filter_path"));
        }

        if (args.containsKey("not_filter_path")) {
            notFilterPath = Pattern.compile(args.get("not_filter_path"));
        }

        boolean verbose = false;

        if (args.containsKey("verbose") && args.get("verbose").equalsIgnoreCase("true")) {
            verbose = true;
        }

        if (!verbose) {
            if (!level.equals("PING")) {
                morpheus.pr("[rd]setting level to PING - no need to get more info[r]");
                level = "PING";
            }
        }

        morpheus.pr("[c1]sending status ping[r]....(waiting " + sl + "s for answers)\n");
        var msg = new Msg(morpheus.getMessaging().getStatusInfoListenerName(), "ALL", level, sl * 1000);
        msg.setMsgId(new MorphiumId());
        final int timeout = sl;
        // var results = morpheus.getMessaging().sendAndAwaitAnswers(msg, expectAnswers, sl * 1000);
        morpheus.getMessaging().addMessageListener(this);
        Thread.sleep(1000);
        sentMessageId = msg.getMsgId();
        int counter = 0;
        morpheus.getMessaging().sendMessage(msg);
        long start = System.currentTimeMillis();

        while (true) {
            morpheus.pr("Waiting max. " + timeout + "s for " + expectAnswers + " answers - got " + answers.size() + " answers after " + counter + "s");

            if (expectAnswers > 0 && answers.size() >= expectAnswers) {
                break;
            }

            if (timeout > 0 && System.currentTimeMillis() - start > timeout * 1000) {
                break;
            }

            counter++;
            Thread.sleep(1000);
        }

        long sendTS = start;
        morpheus.pr("[header1]got messages:[r]" + answers.size());
        answers.sort(new Comparator<Msg>() {
            @Override
            public int compare(Msg o1, Msg o2) {
                return o1.getSenderHost().compareTo(o2.getSenderHost());
            }
        });

        if (!verbose) {
            morpheus.pr("[c3]" + morpheus.getColumn("Sender", 25) + " | " + morpheus.getColumn("Host", 25) + " | " + morpheus.getColumn("After ms", 8));
        }

        for (Msg r : answers) {
            if (filterHost != null && !filterHost.matcher(r.getSenderHost()).matches()) {
                continue;
            }

            if (filterSender != null && !filterSender.matcher(r.getSender()).matches()) {
                continue;
            }

            if (verbose) {
                morpheus.pr("\n\n[header1]Anwser:[r]");
                morpheus.pr("Answer from: [c3]" + r.getSender() + "[r] on host [good]" + r.getSenderHost() + "[r] after [warning]" + (r.getTimestamp() - sendTS) + "ms[r]");

                if (r.getMapValue() != null) {
                    printMap(morpheus, r.getMapValue(), "", keys, filterPath, notFilterPath);
                }
            } else {
                long after = (r.getTimestamp() - sendTS);
                morpheus.pr(morpheus.getColumn(r.getSender(), 25) + " | " + morpheus.getColumn(r.getSenderHost(), 25) + " | " + after + "ms");
                // morpheus.pr("Answer from: [c3]" + r.getSender() + "[r] on host [good]" + r.getSenderHost() + "[r] after [warning]" + (r.getTimestamp() - sendTS) + "ms[r]");
            }
        }
    }

    private void printMap(Morpheus morpheus, Map<String, Object> map, String path, List<String> keys, Pattern pathPattern, Pattern notFilterPath) {
        for (Map.Entry<String, Object> k : map.entrySet()) {
            if (keys != null && !keys.isEmpty() && !keys.contains(k.getKey())) {
                continue;
            }

            if (k.getKey().equals("message_listeners_by_name")) {
                if (pathPattern != null && !pathPattern.matcher(path + ".registered Listeners:").matches()) {
                    continue;
                }

                if (notFilterPath != null && notFilterPath.matcher(path + ".registered Listeners:").matches()) {
                    continue;
                }

                morpheus.pr("[c3]" + path + ".registered Listeners:[r]");
                String[] lst = k.getValue().toString().replaceAll("[{}]+", "").split(",");

                for (String l : lst) {
                    morpheus.pr("[good]" + l.substring(0, l.indexOf("=")) + "[r]");
                }
            } else if (k.getValue() instanceof List) {
                if (((List)k.getValue()).isEmpty()) continue;

                if (pathPattern != null && !pathPattern.matcher(path + "." + k.getKey()).matches()) {
                    continue;
                }

                if (notFilterPath != null && notFilterPath.matcher(path + "." + k.getKey()).matches()) {
                    continue;
                }

                morpheus.pr("[good]" + path + "." + k.getKey() + "[r]");

                for (Object l : (List)k.getValue()) {
                    if (l instanceof Map) {
                        printMap(morpheus, (Map<String, Object>)l, path + "." + k.getKey(), keys, pathPattern, notFilterPath);
                    } else {
                        morpheus.pr("[good]" + l.toString() + "[r]");
                    }
                }

                // morpheus.pr("      [c3]" + morpheus.getColumn(k.getKey(), 25) + "[r] List!");
            } else if (k.getValue() instanceof Map) {
                if (pathPattern != null && !pathPattern.matcher(path + "." + k.getKey()).matches()) {
                    continue;
                }

                if (notFilterPath != null && notFilterPath.matcher(path + "." + k.getKey()).matches()) {
                    continue;
                }

                morpheus.pr("[good]" + path + "." + k.getKey() + "[r]");
                printMap(morpheus, (Map<String, Object>)k.getValue(), path + "." + k.getKey(), keys, pathPattern, notFilterPath);
                //morpheus.pr("      [c3]" + morpheus.getColumn(k.getKey(), 35) + "[r]");
                //Map<String, Object> m = (Map<String, Object>)k.getValue();
                //
                //for (var e : m.entrySet()) {
                //    morpheus.pr("      " + morpheus.getColumn("", 35) + " [good]" + morpheus.getColumn(e.getKey(), 35) + ":[r]  " + e.getValue());
                //}
            } else {
                if (pathPattern != null && !pathPattern.matcher(path + "." + k.getKey()).matches()) {
                    continue;
                }

                if (notFilterPath != null && notFilterPath.matcher(path + "." + k.getKey()).matches()) {
                    continue;
                }

                morpheus.pr("[c3]" + path + "." + k.getKey() + "[r] | " + k.getValue());
            }
        }
    }

    @Override
    public Msg onMessage(Messaging msg, Msg m) {
        if (sentMessageId != null && m.getInAnswerTo() != null && m.getInAnswerTo().equals(sentMessageId)) {
            m.setTimestamp(System.currentTimeMillis()); //avoid problems with different clocks
            answers.add(m);
        }

        return null;
    }
}

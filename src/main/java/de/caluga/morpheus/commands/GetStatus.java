package de.caluga.morpheus.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.json.simple.parser.JSONParser;
import de.caluga.morpheus.ICommand;
import de.caluga.morpheus.Morpheus;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;

public class GetStatus implements ICommand {
    public final static String NAME = "get_status";
    public final static String DESCRIPTION =
        "getting status of all connected nodes, params wait=secs, verbose=true/false, filter_host=PATTERN, filter_sender=PATTERN, keys=LIST_OF_KEYS, path_pattern=PATTERN";


    @Override
    public void execute(Morpheus morpheus, Map<String, String> args) throws Exception {
        int sl = 30;
        Pattern filterHost = null;
        Pattern filterPath = null;
        Pattern filterSender = null;
        List<String> keys = new ArrayList<>();

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

        if (args.containsKey("path_pattern")) {
            filterPath = Pattern.compile(args.get("path_pattern"));
        }

        morpheus.pr("[c1]sending status ping[r]....(waiting " + sl + "s for answers)\n");
        long sendTS = System.currentTimeMillis();
        final int timeout = sl;
        new Thread(()-> {
            for (int i = 0; i < timeout; i++) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }

                System.out.print("\u001B[1A\r");
                morpheus.pr("" + i);
            }
        }).start();
        var msg = new Msg(morpheus.getMessaging().getStatusInfoListenerName(), "ALL", "ALL", sl * 1000);
        msg.setMsgId(new MorphiumId());
        var results = morpheus.getMessaging().sendAndAwaitAnswers(msg, 40, sl * 1000);
        morpheus.pr("[header1]got messages:[r]" + results.size());
        boolean verbose = false;

        if (args.containsKey("verbose") && args.get("verbose").equalsIgnoreCase("true")) {
            verbose = true;
        }

        results.sort(new Comparator<Msg>() {
            @Override
            public int compare(Msg o1, Msg o2) {
                return o1.getSenderHost().compareTo(o2.getSenderHost());
            }
        });

        if (!verbose) {
            morpheus.pr("[c3]" + morpheus.getColumn("Sender", 25) + " | " + morpheus.getColumn("Host", 25) + " | " + morpheus.getColumn("After ms", 8));
        }

        for (Msg r : results) {
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
                    printMap(morpheus, r.getMapValue(), "", keys, filterPath);
                }
            } else {
                long after = (r.getTimestamp() - sendTS);
                morpheus.pr(r.getSender() + " | " + morpheus.getColumn(r.getSenderHost(), 25) + " | " + after + "ms");
                // morpheus.pr("Answer from: [c3]" + r.getSender() + "[r] on host [good]" + r.getSenderHost() + "[r] after [warning]" + (r.getTimestamp() - sendTS) + "ms[r]");
            }
        }
    }

    private void printMap(Morpheus morpheus, Map<String, Object> map, String path, List<String> keys, Pattern pathPattern) {
        for (Map.Entry<String, Object> k : map.entrySet()) {
            if (keys != null && !keys.isEmpty() && !keys.contains(k.getKey())) {
                continue;
            }

            if (k.getKey().equals("message_listeners_by_name")) {
                if (pathPattern != null && !pathPattern.matcher(path + ".registered Listeners:").matches()) {
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

                morpheus.pr("[good]" + path + "." + k.getKey() + "[r]");

                for (Object l : (List)k.getValue()) {
                    if (l instanceof Map) {
                        printMap(morpheus, (Map<String, Object>)l, path + "." + k.getKey(), keys, pathPattern);
                    } else {
                        morpheus.pr("[good]" + l.toString() + "[r]");
                    }
                }

                // morpheus.pr("      [c3]" + morpheus.getColumn(k.getKey(), 25) + "[r] List!");
            } else if (k.getValue() instanceof Map) {
                if (pathPattern != null && !pathPattern.matcher(path + "." + k.getKey()).matches()) {
                    continue;
                }

                morpheus.pr("[good]" + path + "." + k.getKey() + "[r]");
                printMap(morpheus, (Map<String, Object>)k.getValue(), path + "." + k.getKey(), keys, pathPattern);
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

                morpheus.pr("[c3]" + path + "." + k.getKey() + "[r] | " + k.getValue());
            }
        }
    }
}

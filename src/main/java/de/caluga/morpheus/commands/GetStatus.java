package de.caluga.morpheus.commands;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.json.simple.parser.JSONParser;
import de.caluga.morpheus.ICommand;
import de.caluga.morpheus.IRequiresMorphium;
import de.caluga.morpheus.Morpheus;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.MessageListener;
import de.caluga.morphium.messaging.MorphiumMessaging;
import de.caluga.morphium.messaging.Msg;

public class GetStatus implements IRequiresMorphium  {
    public final static String NAME = "get_status";
    public final static String DESCRIPTION =
                    "getting status of all connected nodes, params wait=secs, verbose=true/false, expect_answers=NUM, filter_host=PATTERN, level=[PING,MESSAGING_ONLY,MORPHIUM_ONLY|ALL], filter_sender=PATTERN, keys=LIST_OF_KEYS, filter_path=PATTERN, not_filter_path=PATTERN graphite=host:port";

    private MorphiumId sentMessageId;
    private List<Msg> answers = new ArrayList<>();
    // private DatagramSocket graphiteSocket = null;
    private Socket graphiteSocket = null;
    private String graphite = null;
    private String graphiteHost = null;
    private int graphitePort = 0;
    private PrintWriter graphiteOut = null;
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

        if (args.containsKey("graphite")) {
            graphite = args.get("graphite");
            graphiteHost = graphite;
            graphitePort = 8125;

            if (graphite.contains(":")) {
                graphitePort = Integer.parseInt(graphite.split(":")[1]);
                graphiteHost = graphiteHost.split(":")[0];
            }

            morpheus.pr("Preparing Socket to graphite...");

            try {
                // graphiteSocket = new DatagramSocket();
                graphiteSocket = new Socket(graphiteHost, graphitePort);
                graphiteOut = new PrintWriter(new OutputStreamWriter(graphiteSocket.getOutputStream()));
            } catch (Exception e) {
                morpheus.pr("[rd]FAILED[r]");
                e.printStackTrace();
                System.exit(1);
            }
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
        msg.setProcessedBy(new ArrayList());
        final int timeout = sl;
        sentMessageId = msg.getMsgId();
        final var exp = expectAnswers;
        AtomicBoolean gotResults = new AtomicBoolean(false);
        new Thread(()-> {
            morpheus.pr("[c1]sending...[r]");
            var results = morpheus.getMessaging().sendAndAwaitAnswers(msg, exp, timeout * 1000);

            for (var m : results) {
                answers.add(m);
            }
            gotResults.set(true);
            morpheus.pr("[c2]got results...[r]: " + answers.size());

        }).start();
        int counter = 0;
        long start = System.currentTimeMillis();
        int filteredHosts = 0;
        int filteredOutput = 0;
        morpheus.pr("Waiting max. " + timeout + "s for " + expectAnswers);

        while (!gotResults.get()) {
            counter++;
            Thread.sleep(1000);

            if (gotResults.get()) break;

            if (timeout > 0 && System.currentTimeMillis() - start > (timeout + 2) * 1000) {
                break;
            }

            morpheus.pr("Waiting... " + counter + "s");
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
                filteredHosts++;
                continue;
            }

            if (filterSender != null && !filterSender.matcher(r.getSender()).matches()) {
                filteredHosts++;
                continue;
            }

            if (verbose) {
                morpheus.pr("\n\n[header1]Anwser:[r]");
                morpheus.pr("Answer from: [c3]" + r.getSender() + "[r] on host [good]" + r.getSenderHost() + "[r] after [warning]" + (r.getTimestamp() - sendTS) + "ms[r]");
                sendToGraphite("answer.sender." + r.getSender(), r.getTimestamp() - sendTS);
                sendToGraphite("answer.host." + r.getSenderHost(), r.getTimestamp() - sendTS);

                if (r.getMapValue() != null) {
                    filteredOutput += printMap(morpheus, r.getMapValue(), "", keys, filterPath, notFilterPath, r.getSender());
                }
            } else {
                long after = (r.getTimestamp() - sendTS);
                morpheus.pr(morpheus.getColumn(r.getSender(), 25) + " | " + morpheus.getColumn(r.getSenderHost(), 25) + " | " + after + "ms");
                sendToGraphite("answer.sender." + r.getSender(), after);
                sendToGraphite("answer.host." + r.getSenderHost(), after);
                // morpheus.pr("Answer from: [c3]" + r.getSender() + "[r] on host [good]" + r.getSenderHost() + "[r] after [warning]" + (r.getTimestamp() - sendTS) + "ms[r]");
            }
        }

        morpheus.pr("\n");
        morpheus.pr("processed messages: " + answers.size() + "  Filtered hosts: " + filteredHosts + "    filtered Output: " + filteredOutput, 2);

        if (graphiteSocket != null) {
            graphiteOut.flush();
            graphiteSocket.close();
        }
    }

    private void sendToGraphite(String key, Object value) {
        if (graphiteSocket != null) {
            key = key.replaceAll(":", ".");

            if (key.startsWith(".")) {
                key = key.substring(1);
            }

            System.out.println("sending to graphite: '" + key + ":" + value + "|g'");

            try {
                // byte[] buf = (key + ":" + value.toString() + "|g\n").getBytes();
                // DatagramPacket pak = new DatagramPacket(buf, buf.length, InetAddress.getByName(graphiteHost), graphitePort);
                // graphiteSocket.send(pak);
                graphiteOut.println(key + " " + value + " " + (System.currentTimeMillis() / 1000));
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private int printMap(Morpheus morpheus, Map<String, Object> map, String path, List<String> keys, Pattern pathPattern, Pattern notFilterPath, String sender) {
        int filtered = 0;

        for (Map.Entry<String, Object> k : map.entrySet()) {
            // System.out.println("I am here! " + keys);
            if (keys != null && !keys.isEmpty() && !keys.contains(k.getKey())) {
                // System.out.println("Key " + k.getKey() + "is not found");
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

                morpheus.pr("[c3]" + path + ".registered Listeners:[r]");
                String[] lst = k.getValue().toString().replaceAll("[{}]+", "").split(",");

                for (String l : lst) {
                    morpheus.pr("[good]" + l.substring(0, l.indexOf("=")) + "[r]");
                }

                if (graphiteSocket != null) {
                }
            } else if (k.getValue() instanceof List) {
                if (((List)k.getValue()).isEmpty()) continue;

                if (pathPattern != null && !pathPattern.matcher(path + "." + k.getKey()).matches()) {
                    filtered++;
                    continue;
                }

                if (notFilterPath != null && notFilterPath.matcher(path + "." + k.getKey()).matches()) {
                    filtered++;
                    continue;
                }

                morpheus.pr("[good]" + path + "." + k.getKey() + "[r]");

                for (Object l : (List)k.getValue()) {
                    if (l instanceof Map) {
                        filtered += printMap(morpheus, (Map<String, Object>)l, path + "." + k.getKey(), keys, pathPattern, notFilterPath, sender);
                    } else {
                        morpheus.pr("[good]" + l.toString() + "[r]");
                        sendToGraphite(sender + path + "." + k.getKey(), l.toString());
                    }
                }

                // morpheus.pr("      [c3]" + morpheus.getColumn(k.getKey(), 25) + "[r] List!");
            } else if (k.getValue() instanceof Map) {
                if (pathPattern != null && !pathPattern.matcher(path + "." + k.getKey()).matches()) {
                    filtered++;
                    continue;
                }

                if (notFilterPath != null && notFilterPath.matcher(path + "." + k.getKey()).matches()) {
                    filtered++;
                    continue;
                }

                morpheus.pr("[good]" + path + "." + k.getKey() + "[r]");
                filtered += printMap(morpheus, (Map<String, Object>)k.getValue(), path + "." + k.getKey(), keys, pathPattern, notFilterPath, sender);
                //morpheus.pr("      [c3]" + morpheus.getColumn(k.getKey(), 35) + "[r]");
                //Map<String, Object> m = (Map<String, Object>)k.getValue();
                //
                //for (var e : m.entrySet()) {
                //    morpheus.pr("      " + morpheus.getColumn("", 35) + " [good]" + morpheus.getColumn(e.getKey(), 35) + ":[r]  " + e.getValue());
                //}
            } else {
                if (pathPattern != null && !pathPattern.matcher(path + "." + k.getKey()).matches()) {
                    filtered++;
                    continue;
                }

                if (notFilterPath != null && notFilterPath.matcher(path + "." + k.getKey()).matches()) {
                    filtered++;
                    continue;
                }

                morpheus.pr("[c3]" + path + "." + k.getKey() + "[r] | " + k.getValue());
                sendToGraphite(sender + path + "." + k.getKey(), k.getValue());
            }
        }

        return filtered;
    }

}

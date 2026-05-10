package de.caluga.morpheus.commands;

import java.util.Comparator;
import java.util.Map;
import de.caluga.morpheus.ICommand;
import de.caluga.morpheus.Morpheus;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;

public class GetStatus implements ICommand {
    public final static String NAME = "get_status";
    public final static String DESCRIPTION = "getting status of all connected nodes";

    @Override
    public void execute(Morpheus morpheus, Map<String, String> args) throws Exception {
        morpheus.pr("[c1]sending status ping[r]....(waiting 30s for answers)");
        long sendTS = System.currentTimeMillis();
        var msg = new Msg(morpheus.getMessaging().getStatusInfoListenerName(), "ALL", "ALL", 30000);
        msg.setMsgId(new MorphiumId());
        var results = morpheus.getMessaging().sendAndAwaitAnswers(msg, 40, 15000);
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
        if (!verbose){
            morpheus.pr("[c3]"+morpheus.getColumn("Sender",25)+" | "+morpheus.getColumn("Host",25)+" | "+morpheus.getColumn("After ms", 8));
        }
        for (Msg r : results) {
            if (verbose) {
                morpheus.pr("\n\n[header1]Anwser:[r]");
                morpheus.pr("Answer from: [c3]" + r.getSender() + "[r] on host [good]" + r.getSenderHost() + "[r] after [warning]" + (r.getTimestamp() - sendTS) + "ms[r]");

                if (r.getMapValue() != null) {
                    for (Map.Entry<String, Object> k : r.getMapValue().entrySet()) {
                        if (k.getKey().equals("message_listeners_by_name")) {
                            morpheus.pr("         [c2]registered Listeners:[r]");
                            String[] lst = k.getValue().toString().replaceAll("[{}]+", "").split(",");

                            for (String l : lst) {
                                morpheus.pr("            [good]" + l.substring(0, l.indexOf("=")) + "[r]");
                            }
                        } else {
                            morpheus.pr("      [c3]" + morpheus.getColumn(k.getKey(), 25) + "[r] | " + k.getValue());
                        }
                    }
                }
            } else {
                long after=(r.getTimestamp() - sendTS);
                morpheus.pr(morpheus.getColumn(r.getSender(),25)+" | "+morpheus.getColumn(r.getSenderHost(),25)+" | "+morpheus.getColumn(""+after+"ms", 8));
                // morpheus.pr("Answer from: [c3]" + r.getSender() + "[r] on host [good]" + r.getSenderHost() + "[r] after [warning]" + (r.getTimestamp() - sendTS) + "ms[r]");
            }
        }
    }



}

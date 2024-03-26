package de.caluga.morpheus.commands;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.json.simple.parser.JSONParser;
import de.caluga.morpheus.ICommand;
import de.caluga.morpheus.Morpheus;
import de.caluga.morphium.driver.MorphiumId;
import de.caluga.morphium.messaging.Msg;

public class GetStatus implements ICommand {
    public final static String NAME = "get_status";
    public final static String DESCRIPTION = "getting status of all connected nodes, params wait=secs, verbose=true/false";

    @Override
    public void execute(Morpheus morpheus, Map<String, String> args) throws Exception {
        int sl=30;
        if (args.containsKey("wait")){
            sl=Integer.parseInt(args.get("wait"));
        }
        morpheus.pr("[c1]sending status ping[r]....(waiting "+sl+"s for answers)\n");
        long sendTS = System.currentTimeMillis();
        final int timeout=sl;
        new Thread(()->{
            for (int i=0;i<timeout;i++){
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
                System.out.print("\u001B[1A\r");
                morpheus.pr(""+i);
            }
        }).start();
        var msg = new Msg(morpheus.getMessaging().getStatusInfoListenerName(), "ALL", "ALL", sl*1000);
        msg.setMsgId(new MorphiumId());
        var results = morpheus.getMessaging().sendAndAwaitAnswers(msg, 40, sl*1000);
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
                            morpheus.pr("      "+morpheus.getColumn("[c3]registered Listeners:[r]",25));
                            String[] lst = k.getValue().toString().replaceAll("[{}]+", "").split(",");

                            for (String l : lst) {
                                morpheus.pr(morpheus.getColumn("", 34)+"[good]" + l.substring(0, l.indexOf("=")) + "[r]");
                            }
                        } else if (k.getValue() instanceof List){
                            if (((List)k.getValue()).isEmpty()) continue;
                            morpheus.pr(morpheus.getColumn("[good]"+k.getKey()+"[r]", 25));

                            for (Object l : (List)k.getValue()) {
                                morpheus.pr(morpheus.getColumn("", 34)+"[good]" + l.toString() + "[r]");
                            }
                            // morpheus.pr("      [c3]" + morpheus.getColumn(k.getKey(), 25) + "[r] List!");
                        } else if (k.getValue() instanceof Map){
                            morpheus.pr("      [c3]" + morpheus.getColumn(k.getKey(), 35) + "[r]");
                                Map<String,Object> m=(Map<String,Object>)k.getValue();
                                for (var e:m.entrySet()){
                                    morpheus.pr("      "+morpheus.getColumn("", 35)+" [good]"+morpheus.getColumn(e.getKey(),35)+":[r]  "+e.getValue());
                                }

                        } else {
                            morpheus.pr("      [c3]" + morpheus.getColumn(k.getKey(), 35) + "[r] | " + k.getValue());
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

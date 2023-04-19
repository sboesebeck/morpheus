package de.caluga.morpheus.commands;

import java.util.Comparator;
import java.util.Map;
import de.caluga.morpheus.ICommand;
import de.caluga.morpheus.Morpheus;
import de.caluga.morphium.messaging.Msg;

public class GetStatus implements ICommand{
    public final static String NAME = "get_status";
    public final static String DESCRIPTION="getting status of all connected nodes";
    @Override
    public void execute(Morpheus morpheus, Map<String, String> args) throws Exception {
        morpheus.pr("[c1]sending status ping[r]....");
        long sendTS=System.currentTimeMillis();
        var results=morpheus.getMessaging().sendAndAwaitAnswers(new Msg(morpheus.getMessaging().getStatusInfoListenerName(),"ALL","VAL",30000), 40, 15000);

        morpheus.pr("[header1]got messages:[r]"+results.size());
        results.sort(new Comparator<Msg>() {

            @Override
            public int compare(Msg o1, Msg o2) {
                return o1.getSender().compareTo(o2.getSender());

            }

        });
        for (Msg r:results){
            morpheus.pr("Answer from: [c3]"+r.getSender()+"[r] on host [good]"+r.getSenderHost()+"[r] after [warning]"+(r.getTimestamp()-sendTS)+"ms[r]");
        }

    }




}

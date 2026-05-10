package de.caluga.morpheus.commands;

import java.util.Map;
import de.caluga.morpheus.ICommand;
import de.caluga.morpheus.Morpheus;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.changestream.ChangeStreamEvent;
import de.caluga.morphium.changestream.ChangeStreamListener;
import de.caluga.morphium.messaging.Msg;

public class MessageMonitor implements ICommand {
    public final static String NAME = "monitor";
    public final static String DESCRIPTION = "messaging monitor, will show messages as they are being sent and answered";

    @Override
    public void execute(Morpheus morpheus, Map<String, String> args) throws Exception {
        Morphium m = morpheus.getMorphium();
        m.watch(Msg.class, true, new ChangeStreamListener() {
            @Override
            public boolean incomingData(ChangeStreamEvent evt) {
                if (evt.getOperationType().equals("delete")) return true;
                if (evt.getFullDocument() != null) {
                    Msg m=morpheus.getMorphium().getMapper().deserialize(Msg.class,evt.getFullDocument());
                    morpheus.pr("Evt: " + evt.getOperationType() +"Sender: "+m.getSender()+ "    " + m.getName()+"    "+m.isAnswer());
                } else {
                    return true;
                }

                return true;
            }
        });
    }

}

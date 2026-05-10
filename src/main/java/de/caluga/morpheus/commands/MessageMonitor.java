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
            private int count=0;
            @Override
            public boolean incomingData(ChangeStreamEvent evt) {
                count++;
                if (count%100==1){
                    morpheus.pr("[c3]"+morpheus.getColumn("#", 5)+"|"+morpheus.getColumn("Sender",25)+"|"
                        +morpheus.getColumn("Recipient ", 25)+"|"
                        +morpheus.getColumn("Name", 35)+"|"
                        +morpheus.getColumn("Answer", 10)+"[r]"
                    );
                }

                if (evt.getOperationType().equals("delete")) return true;
                if (evt.getFullDocument() != null) {
                    Msg m=morpheus.getMorphium().getMapper().deserialize(Msg.class,evt.getFullDocument());
                    String color="";
                    if (count%3==0){
                        color="[c3]";
                    }
                    morpheus.pr(color+morpheus.getColumn(""+count,5)+"|"+morpheus.getColumn(m.getSender(),25)+"|"
                        +morpheus.getColumn(m.getRecipients()!=null && !m.getRecipients().isEmpty()?m.getRecipients().get(0):"", 25)+"|"
                        +morpheus.getColumn(m.getName(), 35)+"|"
                        +morpheus.getColumn(m.isAnswer()?"[c2]YES":"[c3]", 10)+"[r]"
                    );
                    // morpheus.pr("Evt: " + evt.getOperationType() +"Sender: "+m.getSender()+ "    " + m.getName()+"    "+m.isAnswer());
                } else {
                    return true;
                }

                return true;
            }
        });
    }

}

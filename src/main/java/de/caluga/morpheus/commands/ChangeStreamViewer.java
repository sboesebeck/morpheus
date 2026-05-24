package de.caluga.morpheus.commands;

import java.util.Map;

import de.caluga.morpheus.IRequiresMorphium;
import de.caluga.morpheus.Morpheus;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.changestream.ChangeStreamEvent;
import de.caluga.morphium.changestream.ChangeStreamListener;

public class ChangeStreamViewer implements IRequiresMorphium {

    public final static String NAME = "changestream";
    @Override
    public void execute(Morpheus morpheus, Map<String, String> args) throws Exception {
        Morphium m = morpheus.getMorphium();
        m.watchDb(true, new ChangeStreamListener() {

            @Override
            public boolean incomingData(ChangeStreamEvent arg0) {
                System.out.println("Incoming event..." +  arg0.getOperationType()) ;
                System.out.println("     ns : " + arg0.getNs());
                System.out.println("     doc: " + arg0.getFullDocument());
                return true;
            }
        });

    }


}

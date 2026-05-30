package de.caluga.morpheus.cli;

import de.caluga.morphium.changestream.ChangeStreamEvent;
import de.caluga.morphium.changestream.ChangeStreamListener;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

@Command(name = "watch", description = "Raw change stream debug viewer - prints every DB event.",
         mixinStandardHelpOptions = true)
public class WatchCommand implements Callable<Integer> {

    @ParentCommand RootCommand parent;

    @Override
    public Integer call() throws Exception {
        MorpheusContext ctx = parent.context();
        ctx.printBanner();
        ctx.connect();
        ctx.getMorphium().watchDb(true, new ChangeStreamListener() {
            @Override
            public boolean incomingData(ChangeStreamEvent evt) {
                System.out.println("Incoming event..." + evt.getOperationType());
                System.out.println("     ns : " + evt.getNs());
                System.out.println("     doc: " + evt.getFullDocument());
                return true;
            }
        });
        return 0;
    }
}

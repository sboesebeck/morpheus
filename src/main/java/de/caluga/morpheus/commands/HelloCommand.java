package de.caluga.morpheus.commands;

import java.util.Map;
import de.caluga.morpheus.ICommand;
import de.caluga.morpheus.Morpheus;
import de.caluga.morpheus.Morpheus.Gradient;

public class HelloCommand implements ICommand {
    public final static String NAME = "hello";
    public final static String DESCRIPTION="Little Hello World-Test Command";

    @Override
    public void execute(Morpheus morpheus,Map<String, String> args) throws Exception {
        System.out.println("Hello World!");

        for (int i =0; i<255;i++){
            System.out.println(morpheus.getAnsiFGColor(i)+"Color: "+i+morpheus.ansiReset());
        }

        morpheus.pr("This is a little test of our project - a text print out with gradient grey!",Gradient.grey);
        morpheus.pr("and now a text print out with gradient green!",Gradient.green);
        morpheus.pr("and now a text print out with gradient in red!",Gradient.red);
        morpheus.pr("and now a text print out with gradient in blue!",Gradient.blue);
        morpheus.pr("and now a text print out with gradient in Yellow!",Gradient.yellow);
    }

}

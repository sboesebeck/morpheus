package de.caluga.morpheus.commands;

import java.io.OutputStreamWriter;
import java.util.Map;
import de.caluga.morpheus.ICommand;
import de.caluga.morpheus.Morpheus;
import de.caluga.morpheus.Version;
import de.caluga.morpheus.Morpheus.Gradient;

public class HelloCommand implements ICommand {
    public final static String NAME = "hello";
    public final static String DESCRIPTION="Little Hello World-Test Command";

    @Override
    public void execute(Morpheus morpheus,Map<String, String> args) throws Exception {
        morpheus.pr("Current Version: "+Version.VERSION);

        for (int i =0; i<255;i++){
            System.out.print("Color:   ");
            System.out.print(morpheus.getAnsiFGColor(i)+i);
            if (i>=254) break;
            System.out.print("     /     ");
            System.out.print(morpheus.getAnsiFGColor(i+1)+(i+1));

            if (i>=253) break;
            System.out.print("     /     ");
            System.out.print(morpheus.getAnsiFGColor(i+2)+(i+2));

            if (i>=252) break;
            System.out.print("     /     ");
            System.out.print(morpheus.getAnsiFGColor(i+3)+(i+3));

            System.out.println(morpheus.ansiReset());
            i++;
            i++;
            i++;
        }
            System.out.println(morpheus.ansiReset());

        morpheus.pr("This is a little test of our project - a text print out with gradient grey!",Gradient.grey);
        morpheus.pr("and now a text print out with gradient green!",Gradient.green);
        morpheus.pr("and now a text print out with gradient in red!",Gradient.red);
        morpheus.pr("and now a text print out with gradient in blue!",Gradient.blue);
        morpheus.pr("and now a text print out with gradient in yellow!",Gradient.yellow);

        morpheus.pr("Morphium config:");
        morpheus.getMorphium().getConfig().asProperties().store(new OutputStreamWriter(System.out),"");
   }

}

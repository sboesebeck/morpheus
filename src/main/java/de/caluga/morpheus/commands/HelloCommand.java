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
        for(Gradient gr:Gradient.values()){
            morpheus.pr("This is a little test of our coloring skills using the Gradient "+gr.name(),gr);
        }

        morpheus.pr("[header1]Morphium config:[r]");
        morpheus.getMorphium().getConfig().asProperties().store(new OutputStreamWriter(System.out),"");
        morpheus.pr("[header1]Theme settings:[r]");
        morpheus.pr("[c1]color1[r] [c2]color2[r] [c3]color3[r] [warning]warning[r] [good]good[r] [error]error[r]");
        morpheus.pr("[header2]  subheder: gradients[r]");
        morpheus.pr("====> Theme gradient number 1 <========",1);
        morpheus.pr("====> Theme gradient number 2 <========",2);
        morpheus.pr("====> Theme gradient number 3 <========",3);

   }

}

package de.caluga.morpheus.commands;

import java.util.List;
import java.util.Map;
import de.caluga.morpheus.ICommand;
import de.caluga.morpheus.Morpheus;

public class ListCommands implements ICommand {
    public final static String NAME = "list";
    public final static String DESCRIPTION = "This little command takes no arguments, just lists all existing commands";

    @Override
    public void execute(Morpheus morpheus, Map<String, String> args) throws Exception {
        for (var cls : morpheus.getCommandClasses()) {
            String name = morpheus.getNameFromCommandClass(cls);
            String desc = morpheus.getDocumentationFromCommandClass(cls);

            //generating table
            morpheus.pr("[header1]Command "+name+"[r]\n   ===> "+desc+"\n");

        }

    }

}

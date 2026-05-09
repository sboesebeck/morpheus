package de.caluga.morpheus.commands;

import java.util.Map;
import de.caluga.morpheus.ICommand;
import de.caluga.morpheus.Morpheus;

public class HelloCommand implements ICommand {
    public final static String NAME = "hello";
    public final static String DESCRIPTION="Little Hello World-Test Command";

    @Override
    public void execute(Morpheus morpheus,Map<String, String> args) throws Exception {
        System.out.println("Hello World!");
        String line = "Hello world!";
    }

}

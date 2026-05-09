package de.caluga.morpheus;

import java.util.Map;

public interface ICommand {
    void execute(Morpheus morpheus,Map<String,String> args) throws Exception ;
}

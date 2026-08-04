package de.caluga.morpheus.core.uptime;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.driver.commands.HelloCommand;
import de.caluga.morphium.driver.wire.HelloResult;
import de.caluga.morphium.driver.wire.MongoConnection;

/** HelloSource über den Morphium-Driver: fragt `hello` ab und liefert den Primary-Host.
 *  HelloCommand setzt $db selbst („admin“) — kein setDb nötig. PoppyDB beantwortet hello
 *  ebenfalls; fehlt das primary-Feld, liefert getPrimary() null und der Watcher bleibt still. */
public class DriverHelloSource implements ReplicaSetWatcher.HelloSource {

    private final Morphium morphium;

    public DriverHelloSource(Morphium morphium) { this.morphium = morphium; }

    @Override
    public String primary() throws Exception {
        var driver = morphium.getDriver();
        MongoConnection con = driver.getReadConnection(null);
        try {
            HelloResult res = new HelloCommand(con).setIncludeClient(false).execute();
            return res.getPrimary();
        } finally {
            driver.releaseConnection(con);
        }
    }
}

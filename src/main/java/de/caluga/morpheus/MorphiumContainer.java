package de.caluga.morpheus;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Component
public class MorphiumContainer {
    @Autowired
    private Application app;


    private Morphium morphiumConnection=null;
    private Integer morphiumConnectionId=null;
    private String morphiumConnectionName=null;
    private String morphiumConnectionDescription=null;

    private Logger log= LoggerFactory.getLogger(MorphiumContainer.class);

    @PostConstruct
    public void init(){
        app.addError("Startup...");
        try {
            Map<Integer, Properties> settings = app.getSettingsList();
            for (Integer key: settings.keySet()){
                Properties props = settings.get(key);
                String prefix = "morpheus." + key;
                String connName = props.getProperty(prefix + ".name");
                if (props.getProperty("morpheus."+key+".autoConnect","false").equals("true")){
                    new Thread(){
                        public void run(){
                            try {
                                app.addError("Connecting to "+connName);
                                connectTo(key);
                                app.addError("Connection to "+ connName +" etablished!");
                            }catch(Exception e){
                                app.addError("Conncting to "+connName+" failed!!!");
                            }
                        }
                    }.start();

                    return;
                } else {
                    log.info(connName+" not connected - no autoConnect! "+props.getProperty(prefix+".autoConnect"));
                }
            }
        }catch(Exception e){
            log.error("Something went wrong!");
            app.addError("MorphiumConnection fail: "+e.getMessage());
        }
    }

    public void connectTo(Integer id) throws IOException {
        if (morphiumConnection!=null){
            throw new IllegalArgumentException("Already connected");
        }
        Properties p=app.readSettings();
        MorphiumConfig cfg= MorphiumConfig.fromProperties("morpheus."+id,p);
        morphiumConnection=new Morphium(cfg);
        morphiumConnectionId=id;
        morphiumConnectionName=p.getProperty("morpheus."+id+".name","unknown");
        morphiumConnectionDescription=p.getProperty("morpheus."+id+".description","-");

    }

    public boolean isConnected(){
        return morphiumConnection!=null;
    }

    public Morphium getMorphiumConnection() {
        return morphiumConnection;
    }

    public Integer getMorphiumConnectionId() {
        return morphiumConnectionId;
    }

    public String getMorphiumConnectionName() {
        return morphiumConnectionName;
    }

    public String getMorphiumConnectionDescription() {
        return morphiumConnectionDescription;
    }

    public void disconnect(){
        if (morphiumConnection==null) throw new IllegalArgumentException("Not connected!");
        try {
            morphiumConnection.close();
        } catch (Exception e) {
            //swallow
        }
        morphiumConnection=null;
        morphiumConnectionId=null;
        morphiumConnectionDescription=null;
        morphiumConnectionName=null;
    }


}

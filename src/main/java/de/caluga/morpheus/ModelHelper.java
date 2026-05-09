package de.caluga.morpheus;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.io.IOException;
import java.util.List;

@Component
public class ModelHelper {
    @Autowired
    private MorphiumContainer mc;
    @Autowired
    private Application app;

    private List<String> collections;
    private long collectionsReadAt=0;

    public void prepareModel(String pageName,String pageTitle, Model m){
        m.addAttribute("pageTitle",pageTitle);
        m.addAttribute("pageName",pageName);
        m.addAttribute("connected",mc.isConnected());
        try {
            m.addAttribute("emptyconfig",app.readSettings().size()==0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mc.isConnected()) {
            if (collections==null || System.currentTimeMillis()-collectionsReadAt>30000){
                collections=mc.getMorphiumConnection().listCollections();
                collectionsReadAt=System.currentTimeMillis();
            }
            m.addAttribute("collections",collections);
            m.addAttribute("connectionName",mc.getMorphiumConnectionName());
            m.addAttribute("connectionDescription",mc.getMorphiumConnectionDescription());
            m.addAttribute("connectionId",mc.getMorphiumConnectionId());
        }
    }
}

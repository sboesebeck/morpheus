package de.caluga.morpheus.controller;

import de.caluga.morpheus.Application;
import de.caluga.morpheus.ModelHelper;
import de.caluga.morpheus.MorphiumContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

@Controller
public class HomeController {
    @Autowired
    private Application app;
    @Autowired
    private MorphiumContainer mc;

    @Autowired
    private ModelHelper mh;

    @GetMapping("/")
    public String home(Model m){
        mh.prepareModel("MainPage","Welcome to Morpheus",m);
        try {
            if (app.readSettings().size()==0 || !mc.isConnected()){
                return configure(m);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "index";
    }


    @GetMapping("/config")
    public String configure(Model m) throws IOException {
        mh.prepareModel("configuration","Configure Morpheus",m);
        return "config";
    }
}

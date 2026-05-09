package de.caluga.morpheus.controller;

import de.caluga.morpheus.ModelHelper;
import de.caluga.morpheus.MorphiumContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CLIController {
    @Autowired
    private ModelHelper mh;

    @Autowired
    private MorphiumContainer mc;

    @GetMapping("/cli")
    public String cli(Model model){
        mh.prepareModel("cli","Command Line Interface",model);
        return("cli");
    }
}

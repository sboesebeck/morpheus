package de.caluga.morpheus.controller;

import de.caluga.morpheus.Application;
import de.caluga.morpheus.ModelHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {
    @Autowired
    Application app;

    @Autowired
    private ModelHelper mh;

    @RequestMapping("/error")
    public String showError(Model m){
        mh.prepareModel("error","an error occured",m);
        return "error";
    }
}

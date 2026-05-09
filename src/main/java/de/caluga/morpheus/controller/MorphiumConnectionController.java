package de.caluga.morpheus.controller;

import de.caluga.morpheus.MorphiumContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class MorphiumConnectionController {

    @Autowired
    private MorphiumContainer morphiumContainer;
    @GetMapping("/connect")
    @ResponseBody
    public String connect(@RequestParam Integer id){
        return "";
    }
}

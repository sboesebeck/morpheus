package de.caluga.morpheus.controller.rest;

import de.caluga.morpheus.Application;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AppErrorController {
    @Autowired
    Application app;

    @GetMapping("/errors")
    @ResponseBody
    public String errors(){
        if (app.getErrors().isEmpty()){
            return "{ \"errorFlag\" : \"false\", \"errorText\" : []}";
        }
        StringBuilder b=new StringBuilder();
        b.append("{ \"errorFlag\" : \"true\",\"errorText\" : [");
        for (Application.ErrorEntry t:app.getErrors()){
            b.append("\"");
            b.append(t.text);
            b.append("\",");

        }
        b.setLength(b.length()-1);

        b.append("]\n }");
        return b.toString();
    }

    @GetMapping("/removeError")
    @ResponseBody
    public String removeError(@RequestParam Integer index){
        app.removeError(index);
        return errors();
    }

}

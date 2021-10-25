package de.caluga.morpheus.controller.rest;

import de.caluga.morpheus.Application;
import de.caluga.morphium.MorphiumConfig;
import org.json.simple.parser.JSONParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Controller
public class ConfigsController {
    @Autowired
    private Application app;

    @GetMapping("/configs")
    @ResponseBody
    public String getConfigs() throws Exception {
        Properties p = app.readSettings();
        StringBuilder s = new StringBuilder();
        s.append("[");
        int c = 1;
        while (true) {
            s.append("{");
            boolean found = false;
            for (String n : p.stringPropertyNames()) {
                if (n.startsWith("morpheus." + c)) {
                    s.append("\"");
                    s.append(n.replaceAll("morpheus." + c + ".", ""));
                    s.append("\" : \"");
                    s.append(p.getProperty(n).replaceAll("\\[", "").replaceAll("\\]", ""));
                    s.append("\",");
                    found = true;
                }
            }
            if (found) {
                s.setLength(s.length() - 1); //remove ,
            } else {
                s.setLength(s.length() - 2); //remove , and {
                break;
            }
            c++;
            s.append("},");

        }
        s.append("]");
        return s.toString();
    }


    @PostMapping("/configs")
    @ResponseBody
    public String storeConfigs(@RequestBody String data, HttpServletRequest req) throws Exception {
        JSONParser parser = new JSONParser();
        Map<String, Object> parsed = (Map<String, Object>) parser.parse(data);
        List<Map> configs = (List<Map>) parsed.get("data");
        Properties p = new Properties();
        int count = 1;
        for (Map m : configs) {
            Properties localP = new Properties();
            localP.putAll(m);
            MorphiumConfig cfg = MorphiumConfig.fromProperties(localP);
            if (cfg.getMaxConnections() == 0)
                cfg.setMaxConnections(10);
            if (cfg.getHousekeepingTimeout()==0){
                cfg.setHousekeepingTimeout(5000);
            }
            p.putAll(cfg.asProperties("morpheus." + count));
            p.setProperty("morpheus." + count + ".name", localP.getProperty("name", "name"));
            p.setProperty("morpheus." + count + ".description", localP.getProperty("description", ""));
            p.setProperty("morpheus." + count + ".collection", localP.getProperty("collection", "collection"));
            p.setProperty("morpheus." + count + ".autoConnect", localP.getProperty("autoConnect", "false"));
            p.setProperty("morpheus." + count + ".id", "" + count);
            count++;
        }
        app.storeSettings(p);
        return getConfigs();
    }
}

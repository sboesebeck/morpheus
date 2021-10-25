package de.caluga.morpheus.controller.rest;

import de.caluga.morpheus.Application;
import de.caluga.morpheus.MorphiumContainer;
import de.caluga.morphium.Utils;
import de.caluga.morphium.driver.MorphiumDriverException;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Controller
public class CollectionOperationsController {
    @Autowired
    private Application app;

    @Autowired
    private MorphiumContainer mc;

    private Logger log= LoggerFactory.getLogger(CollectionOperationsController.class);

    @PostMapping("/createCollection")
    @ResponseBody
    public String storeConfigs(@RequestBody String data) throws Exception {
        if (!mc.isConnected()){
            return "{ \"state\" : \"error\", \"message\" : \"not connected\" }";
        }

        JSONParser parser = new JSONParser();
        Map<String, Object> parsed = (Map<String, Object>) parser.parse(data);

        if (parsed.get("expireAfterSeconds")!=null && parsed.get("expireAfterSeconds").equals(Long.reverse(0))){
            parsed.remove("expireAfterSeconds");
        } else {
            parsed.put("expireAfterSeconds",Integer.parseInt((String) parsed.get("expireAfterSeconds")));
        }
        if (parsed.get("capped")==null || parsed.get("capped").equals(Boolean.FALSE)){
            parsed.remove("capped");
            parsed.remove("size");
            parsed.remove("max");
        } else {
            parsed.put("size",Integer.parseInt((String) parsed.get("size")));
            parsed.put("max",Integer.parseInt((String) parsed.get("max")));
        }

        var copy=new HashMap<>(parsed);
        for (String k:copy.keySet()){
            if (parsed.get(k).equals("")){
                parsed.remove(k);
            }
        }
        if (parsed.get("timeseries")!=null && parsed.get("timeseries")instanceof Map){
            Map m=(Map)parsed.get("timeseries");
            if (m.get("timeField")==null || m.get("timeField").equals("")){
                parsed.remove("timeseries");
            }
        }
        Map<String,Object> cmd= Utils.getMap("create",parsed.get("name"));
        parsed.remove("name");
        try {
            Map<String, Object> res = mc.getMorphiumConnection().getDriver().runCommand(mc.getMorphiumConnection().getDatabase(), cmd);
            if (res.get("ok")!=null && res.get("ok").equals(Double.valueOf(1))) {
                app.addError("Collection created: "+cmd.get("create"));
                return "{ \"state\" : \"ok\" }";
            }
        } catch (MorphiumDriverException e) {
            log.error("Exception creating collection",e);
        }
        app.addError("Error creating collection");
        return "{ \"state\" : \"fail\" }";
    }
}

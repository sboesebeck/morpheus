package de.caluga.morpheus.controller;

import com.mongodb.client.FindIterable;
import de.caluga.morpheus.ModelHelper;
import de.caluga.morpheus.MorphiumContainer;
import de.caluga.morphium.Utils;
import de.caluga.morphium.driver.mongodb.MongoDriver;
import de.caluga.morphium.query.MorphiumIterator;
import jdk.jshell.JShell;
import jdk.jshell.SnippetEvent;
import jdk.jshell.tool.JavaShellToolBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.script.*;
import java.util.ArrayList;
import java.util.List;

@Controller
public class CollectionController {
    @Autowired
    private ModelHelper mh;

    @Autowired
    private MorphiumContainer mc;

    private Logger log= LoggerFactory.getLogger(CollectionController.class);

    @GetMapping("/collection")
    public String collectionView(@RequestParam String collection,@RequestParam(required = false) String query, Model m) {
        mh.prepareModel("collection_view", "Collection " + collection, m);
        if (query!=null) {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("nashorn");
            engine.getContext().setAttribute("mc", mc, ScriptContext.GLOBAL_SCOPE);
            engine.getContext().setAttribute("morphium", mc.getMorphiumConnection(), ScriptContext.GLOBAL_SCOPE);
            engine.getContext().setAttribute("db", ((MongoDriver) mc.getMorphiumConnection().getDriver()).getDb(mc.getMorphiumConnection().getDatabase()), ScriptContext.GLOBAL_SCOPE);
            try {
                Object result = engine.eval(query);
                log.info("Got result: " + result);
                ArrayList<String> resultList = new ArrayList<>();
                m.addAttribute("resultList", resultList);
                if (result instanceof FindIterable) {
                    for (Object o : ((FindIterable) result)) {
                        resultList.add(Utils.toJsonString(o));
                    }
                }else if (result instanceof List){
                    for (Object o : ((MorphiumIterator) result)) {
                        resultList.add(Utils.toJsonString(o));
                    }
                }else if (result instanceof MorphiumIterator){
                    for (Object o : ((MorphiumIterator) result)) {
                        resultList.add(Utils.toJsonString(o));
                    }
                } else if (result != null) {
                    m.addAttribute("result", result.toString());
                }
            } catch (ScriptException e) {
                e.printStackTrace();
            }
        }

        return "collection_view";
    }


    @GetMapping("/create_collection")
    public String createCollectionView(Model m){
        mh.prepareModel("create_collection","Create Collection",m);
//        JShell shell= JShell.builder().
//                build();
//        shell.addToClasspath("");
//        shell.eval("import de.caluga.morphium.*;");
//        shell.eval("import de.caluga.morpheus.*;");
//        JavaShellToolBuilder.builder().env("mc",mc).;
//        List<SnippetEvent> ret = shell.eval("");


        //shell.eval("mc.getMorphium")
        return "create_collection";
    }

}

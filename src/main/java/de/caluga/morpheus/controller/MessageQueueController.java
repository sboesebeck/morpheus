package de.caluga.morpheus.controller;

import de.caluga.morpheus.Application;
import de.caluga.morpheus.ModelHelper;
import de.caluga.morpheus.MorphiumContainer;
import de.caluga.morpheus.data.Message;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.Utils;
import de.caluga.morphium.messaging.Messaging;
import de.caluga.morphium.messaging.Msg;
import de.caluga.morphium.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.util.*;

@Controller
public class MessageQueueController {

    @Autowired
    private MorphiumContainer morphiumContainer;

    @Autowired
    private Application app;

    @Autowired
    private ModelHelper mh;

    private Logger log= LoggerFactory.getLogger(MessageQueueController.class);
    public MessageQueueController(){
//        this.messaging=messaging;
//        this.morphium=morphium;
//        for (String c:morphium.listCollections()){
//            if (c.startsWith("mmsg_")){
//                msgCollections.add(c);
//            } else if (c.equals("msg")){
//                msgCollections.add(c);
//            }
//        }
    }
    @GetMapping("/queue")
    public String messageQueue(Model model) throws IOException {
        mh.prepareModel("Message queue","Messages",model);
        Morphium morphium=morphiumContainer.getMorphiumConnection();
        //Properties p=app.getSettingsList().get(1);
        List<String>msgCollections=new ArrayList<>();
        for (String c:morphium.listCollections()){
            if (c.startsWith("mmsg_")){
                msgCollections.add(c);
            } else if (c.equals("msg")){
                msgCollections.add(c);
            }
        }

        Map<String,List<Message>> queue=new HashMap<>();

        for (String col:msgCollections) {
            log.info("Getting data for "+col);
            Query<Msg> q=morphium.createQueryFor(Msg.class, msgCollections.get(0));
            q.sort(Utils.getMap(Msg.Fields.timestamp.name(),-1).add(Msg.Fields.priority.name(),1));
            q.limit(100);
            List<Message> lst=new ArrayList<>();
            for (Map<String,Object> o:q.asMapList()){
                o.put("class_name",Message.class.getName()); //cheating
                lst.add(morphium.getMapper().deserialize(Message.class,o));
            }
            queue.put(col,lst);
        }
        model.addAttribute("queueByColl",queue);
        return "message_queue";
    }


}

package de.caluga.morpheus.controller;


import de.caluga.morpheus.ModelHelper;
import de.caluga.morpheus.MorphiumContainer;
import de.caluga.morphium.messaging.Messaging;
import de.caluga.morphium.messaging.Msg;
import de.caluga.morphium.messaging.StatusInfoListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Controller
public class MessagingDashboardController {
    public List<String> msgCollections;
    @Autowired
    private ModelHelper mh;

    @Autowired
    private MorphiumContainer mc;

    private boolean inited = false;
    private Logger log = LoggerFactory.getLogger(MessagingDashboardController.class);

    private Map<String, Messaging> messagingByCollection;
    private Map<String, Set<String>> clientsByCollection;

    private ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(10);

    public void init() throws Exception {
        messagingByCollection = new HashMap<>();
        clientsByCollection = new HashMap<>();
        msgCollections = new ArrayList<>();

        if (mc == null || !mc.isConnected()) {
            return;
        }
        inited = true;

        //for Testing purpose
        for (int i = 0; i < 5; i++) {
            Messaging testMsg = new Messaging(mc.getMorphiumConnection(), 100, true);
            testMsg.setSenderId("testLocal_" + i);
            testMsg.start();
            testMsg.addMessageListener((msg, m) -> {
                if (m.getName().equals(msg.getStatusInfoListenerName())) {
                    //log.warn("Incoming status info message!");
                    return null;
                }
                return new Msg("answer", "msg", "value");
            });
            mh.addMessage("Initialising test-messaging subsystems");
        }
        //// end Testing messaging

        List<String> cols = mc.getMorphiumConnection().listCollections();
        for (String c : cols) {
            if (c.equals("msg") || c.startsWith("mmsg_")) {
                if (c.startsWith("mmsg_")) {
                    c = c.substring(5);
                }
                msgCollections.add(c);
                String msgColl = c;
                if (msgColl.equals("msg")) {
                    msgColl = null;
                }
                Messaging m = new Messaging(mc.getMorphiumConnection(), msgColl, 100, false);
                m.start();
                Thread.sleep(250);
                messagingByCollection.put(c, m);
                //Polling thread
                final String msgCollection = c;
                scheduler.scheduleWithFixedDelay(() -> {
                    List<Msg> answers = m.sendAndAwaitAnswers(new Msg(messagingByCollection.get(msgCollection).getStatusInfoListenerName(), "", StatusInfoListener.StatusInfoLevel.MESSAGING_ONLY.name(), 3000), 100, 1000);
                    if (!answers.isEmpty()) {
                        for (Msg msg : answers) {
                            clientsByCollection.putIfAbsent(msgCollection, new HashSet<>());
                            clientsByCollection.get(msgCollection).add(msg.getSender() + "@" + msg.getSenderHost());
                        }
                    }
                }, 1000, 1000, TimeUnit.MILLISECONDS);
                mh.addMessage("init messaging for " + c);
            }
        }

    }

    @GetMapping("/messaging")
    public String messageDashboard(@RequestParam(required = false) String coll, Model model, HttpServletResponse response) throws Exception {
        if (!inited) init();
        if (!mc.isConnected()) {
            mh.addMessage("Error: not connected!");
        }
        mh.prepareModel("messagingDashboard", "Messaging Dashboard", model);

        if (msgCollections.size() != 0) {

            if (coll != null) {
                if (!msgCollections.contains(coll)) {
                    response.sendError(404);
                    return null;
                }
                model.addAttribute("coll", coll);
            } else {
                coll = msgCollections.get(0);
                model.addAttribute("coll", coll);
            }
            model.addAttribute("clients", clientsByCollection.get(coll));
            model.addAttribute("msgCollections", msgCollections);
            model.addAttribute("pending", messagingByCollection.get(coll).getPendingMessagesCount());
            model.addAttribute("total", mc.getMorphiumConnection().createQueryFor(Msg.class).setCollectionName(coll).countAll());


        }
        return "messaging_dashboard";
    }
}

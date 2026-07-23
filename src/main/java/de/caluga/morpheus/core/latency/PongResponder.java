package de.caluga.morpheus.core.latency;

import de.caluga.morphium.messaging.MessageListener;
import de.caluga.morphium.messaging.MorphiumMessaging;
import de.caluga.morphium.messaging.Msg;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Echo responder for the latency topic: answers every ping immediately,
 *  embedding its receive timestamp so the sender can split the path. */
public class PongResponder implements MessageListener<Msg> {

    private final String name;
    private final LongSupplier wallClock;
    private volatile long received = 0;
    private volatile long answered = 0;

    public PongResponder(String name, LongSupplier wallClock) {
        this.name = name;
        this.wallClock = wallClock;
    }

    public long received() { return received; }
    public long answered() { return answered; }

    @Override
    public Msg onMessage(MorphiumMessaging messaging, Msg msg) {
        try {
            if (msg == null || msg.getInAnswerTo() != null) return null;
            received++;
            Msg answer = msg.createAnswerMsg();
            Map<String, Object> map = new HashMap<>();
            map.put("recvTs", wallClock.getAsLong());
            map.put("responder", name);
            if (msg.getMapValue() != null && msg.getMapValue().get("seq") != null) {
                map.put("seq", msg.getMapValue().get("seq"));
            }
            answer.setMapValue(map);
            answered++;
            return answer;
        } catch (RuntimeException e) {
            return null;
        }
    }
}

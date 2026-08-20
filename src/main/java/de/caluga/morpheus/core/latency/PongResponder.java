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
    private final int payloadBytes;
    private final long workMs;
    private volatile long received = 0;
    private volatile long answered = 0;

    public PongResponder(final String name, final LongSupplier wallClock) {
        this(name, wallClock, 0, 0);
    }

    public PongResponder(final String name, final LongSupplier wallClock, final int payloadBytes) {
        this(name, wallClock, payloadBytes, 0);
    }

    public PongResponder(final String name, final LongSupplier wallClock,
                         final int payloadBytes, final long workMs) {
        this.name = name;
        this.wallClock = wallClock;
        this.payloadBytes = payloadBytes;
        this.workMs = workMs;
    }

    public long received() {
        return received;
    }

    public long answered() {
        return answered;
    }

    @Override
    public Msg onMessage(final MorphiumMessaging messaging, final Msg msg) {
        try {
            if (msg == null || msg.getInAnswerTo() != null) {
                return null;
            }
            received++;
            // recvTs is the arrival time, taken BEFORE the simulated work, so the sender's
            // outbound/return split stays honest: work shows up in the return leg (and the RTT),
            // exactly as a real handler's processing time would.
            final long recvTs = wallClock.getAsLong();
            if (workMs > 0) {
                try {
                    Thread.sleep(workMs);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();   // shutting down: don't answer
                    return null;
                }
            }
            final Msg answer = msg.createAnswerMsg();
            final Map<String, Object> map = new HashMap<>();
            map.put("recvTs", recvTs);
            map.put("responder", name);
            if (msg.getMapValue() != null && msg.getMapValue().get("seq") != null) {
                map.put("seq", msg.getMapValue().get("seq"));
            }
            if (payloadBytes > 0) {
                map.put("pad", "x".repeat(payloadBytes));
            }
            answer.setMapValue(map);
            answered++;
            return answer;
        } catch (final RuntimeException e) {
            return null;
        }
    }
}

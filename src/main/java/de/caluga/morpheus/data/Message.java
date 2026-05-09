package de.caluga.morpheus.data;

import de.caluga.morphium.annotations.AdditionalData;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.messaging.Msg;

import java.util.Date;
import java.util.Map;

@Entity
public class Message extends Msg {
    @AdditionalData
    private Map<String,Object> data;

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }


    public Date getTime(){
        return new Date(getTimestamp());
    }
}

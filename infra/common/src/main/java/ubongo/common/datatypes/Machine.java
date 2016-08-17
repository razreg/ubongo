package ubongo.common.datatypes;

import javax.xml.bind.annotation.*;
import java.io.Serializable;

/**
 * Machine holds all required information regarding the physical/virtual machine
 * on which units are running.
 */
@XmlRootElement(name = "machine")
@XmlAccessorType(XmlAccessType.FIELD)
public class Machine implements Serializable {

    @XmlAttribute private int id;
    @XmlElement private String host;
    @XmlElement private String description;

    private boolean connected = false;
    private boolean active = false;
    private java.sql.Timestamp lastHeartbeat;

    public int getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public java.sql.Timestamp getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(java.sql.Timestamp lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
}

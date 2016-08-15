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
    @XmlElement private String address;
    @XmlElement private MachineStatistics machineStatistics;

    private boolean active = false; // after the first ping to the machine this should become true
    private java.sql.Timestamp lastUpdated;

    public MachineStatistics getMachineStatistics() {
        return machineStatistics;
    }

    public int getId() {
        return id;
    }

    public String getAddress() {
        return address;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setStatistics(MachineStatistics machineStatistics) {
        this.machineStatistics = machineStatistics;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public java.sql.Timestamp getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(java.sql.Timestamp lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}

package ubongo.common.datatypes.unit;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;

/**
 * A UnitParameter encapsulates the information about a parameter that a unit may require.
 * For instance, if a unit requires a string, we want to have an identifying name for the parameter,
 * a display name to show in the UI, and a value received from the user.
 * This may also include other information such as whether this parameter is required, etc.
 * This class is extended by several specific parameter classes for the different parameters types.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class UnitParameter implements Cloneable, Serializable {

    private String name;
    private String display;

    @XmlElement(name = "default")
    protected String value;

    public String getName() {
        return name;
    }

    public String getDisplay() {
        return display;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

}

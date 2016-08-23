package ubongo.common.datatypes.unit;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.Serializable;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class StringUnitParameter extends UnitParameter implements Serializable {

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

package ubongo.common.datatypes;

import ubongo.common.datatypes.unit.UnitParameter;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class FileUnitParameter extends UnitParameter {

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

}

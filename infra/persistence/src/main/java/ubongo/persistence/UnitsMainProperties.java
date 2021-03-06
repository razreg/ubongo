package ubongo.persistence;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "units")
@XmlAccessorType(XmlAccessType.FIELD)
public class UnitsMainProperties {

    @XmlElementWrapper (name = "matlabDepenencies")
    @XmlElements({@XmlElement (name = "path", type = String.class)})
    private List<String> matlabDepenencies = new ArrayList<>();

    public List<String> getMatlabDepenencies() {
        return matlabDepenencies;
    }
}

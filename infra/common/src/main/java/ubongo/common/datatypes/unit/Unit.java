package ubongo.common.datatypes.unit;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.bind.annotation.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit is the basic fMRI service that runs on a machine.
 * It contains all the information required to run an fMRI script/program.
 */
@XmlRootElement(name = "unit")
@XmlAccessorType(XmlAccessType.FIELD)
public class Unit implements Serializable, Cloneable {

    private static Logger logger = LogManager.getLogger(Unit.class);
    private static final int BASE_PARAMS = 3;

    @XmlElement
    private String name;

    @XmlElement
    private String description;

    @XmlAttribute
    private int id;

    @XmlElement (name = "input-files")
    private String inputPaths;

    @XmlElement (name = "output-dir")
    private String outputDir;

    @XmlElementWrapper (name = "parameters")
    @XmlElements({@XmlElement (name = "parameter", type = UnitParameter.class)})
    private List<UnitParameter> parameters = new ArrayList<>();

    public Unit(int id) {
        this.id = id;
    }

    public Unit() {}

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public List<UnitParameter> getParameters() {
        return parameters;
    }

    public String getInputPaths() {
        return inputPaths;
    }

    public void setInputPaths(String inputPaths) {
        this.inputPaths = inputPaths;
    }

    public void setParameters(List<UnitParameter> parameters) {
        this.parameters = parameters;
    }

    public void setParameterValues(String json) throws JsonParseException {
        try {
            Map<String, String> jsonMap =
                    new Gson().fromJson(json, new TypeToken<HashMap<String, String>>(){}.getType());
            for (UnitParameter param : parameters) {
                String paramName = param.getName();
                if (jsonMap.get(paramName) != null) {
                    param.setValue(jsonMap.get(paramName));
                }
            }
        } catch (RuntimeException e) {
            logger.fatal("Failed to set parameter values for unit from JSON: " + json);
            throw new JsonParseException(e.getMessage());
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        Unit unit = (Unit) super.clone();
        List<UnitParameter> newParams = new ArrayList<>();
        for (UnitParameter param : unit.parameters) {
            newParams.add((UnitParameter) param.clone());
        }
        unit.parameters = newParams;
        return unit;
    }

    public static String getUnitFileName(long unitId, String suffix) {
        String pattern = "unit_%03d" + suffix;
        return String.format(pattern, unitId);
    }

    public static String getUnitMatlabFileName(long unitId) {
        return getUnitFileName(unitId, ".m");
    }

    public static String getUnitBashFileName(long unitId) {
        return getUnitFileName(unitId, ".sh");
    }
}

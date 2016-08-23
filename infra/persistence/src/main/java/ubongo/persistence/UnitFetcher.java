package ubongo.persistence;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.datatypes.unit.Unit;
import ubongo.persistence.exceptions.UnitFetcherException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The UnitFetcher class supplys a method to get a unit Object given its ID
 */
public class UnitFetcher {

    private static Logger logger = LogManager.getLogger(UnitFetcher.class);
    private static final Pattern UNIT_FILENAME_PATTERN = Pattern.compile("unit_[0-9]{3}.xml");
    private static final Pattern DIGITS_PATTERN = Pattern.compile("-?\\d+");
    private String unitSettingsDirPath;

    public UnitFetcher(String unitSettingsDirPath) {
        this.unitSettingsDirPath = unitSettingsDirPath;
    }

    /**
     * Get a Unit object with unit settings corresponding to those set
     * in the XML configuration file for the unit with unitId.
     * @param unitId is a number required to locate the relevant XML file
     * @return Unit object with the data corresponding to unitId.
     * @throws UnitFetcherException if unit with unitId does not exist
     */
    public Unit getUnit(int unitId) throws UnitFetcherException {
        File file = getUnitSettingsFile(unitId);
        return getUnit(file, unitId);
    }

    private Unit getUnit(File file, int unitId) throws UnitFetcherException {
        Unit unit = null;
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Unit.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            unit = (Unit) unmarshaller.unmarshal(file);
        } catch (JAXBException e) {
            String originalMsg = e.getMessage();
            String msg = "Failed to parse unit settings file for unit " + unitId +
                    " (file path: " + file.getAbsolutePath() + "). " +
                    ((originalMsg == null) ? "" : "Details: " + originalMsg);
            logger.error(msg);
        }
        if (unit == null) {
            throw new UnitFetcherException("Failed to retrieve unit " + unitId +
                    ". Make sure that the unit exists and is configured correctly");
        }
        return unit;
    }

    public Map<Integer,Unit> getAllUnits() throws UnitFetcherException {
        File unitsDir = new File(unitSettingsDirPath);
        File[] files = unitsDir.listFiles((dir, name) -> UNIT_FILENAME_PATTERN.matcher(name).matches());
        Map<Integer,Unit> units = new HashMap<>();
        Unit unit;
        for (File file: files) {
            Matcher matcher = DIGITS_PATTERN.matcher(file.getName());
            if (matcher.find()) {
                unit = getUnit(file, Integer.parseInt(matcher.group()));
                units.put(unit.getId(),unit);
            }
        }
        return units;
    }

    private File getUnitSettingsFile(long unitId) {
        return new File(unitSettingsDirPath, Unit.getUnitFileName(unitId,".xml"));
    }

}

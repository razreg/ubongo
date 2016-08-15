package ubongo.persistence;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.datatypes.Machine;
import ubongo.common.datatypes.MachinesList;
import ubongo.common.network.SSHConnectionProperties;
import ubongo.persistence.db.DBConnectionProperties;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.io.File;
import java.util.List;

@XmlRootElement(name = "configuration")
@XmlAccessorType(XmlAccessType.FIELD)
public class Configuration {

    private static Logger logger = LogManager.getLogger(Configuration.class);

    @XmlElement(name = "db-connection")
    private DBConnectionProperties dbConnectionProperties;

    @XmlElement(name = "ssh-connection")
    private SSHConnectionProperties sshConnectionProperties;

    @XmlElement(name = "machines")
    private MachinesList machinesList;

    @XmlElement(name = "debug")
    private Boolean debug;

    public boolean getDebug() {
        return debug;
    }

    public DBConnectionProperties getDbConnectionProperties() {
        return dbConnectionProperties;
    }

    public SSHConnectionProperties getSshConnectionProperties() {
        return sshConnectionProperties;
    }

    public List<Machine> getMachines() {
        return machinesList.getMachines();
    }

    static public Configuration loadConfiguration(String path) throws UnmarshalException {
        Configuration configuration;
        File file = new File(path);
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(Configuration.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            configuration = (Configuration) unmarshaller.unmarshal(file);
            if (configuration.debug == null) {
                configuration.debug = false;
            }
        } catch (JAXBException e) {
            logger.error("Failed to parse configuration file (file path: "
                    + file.getAbsolutePath() + ").", e);
            configuration = null;
        }
        if (configuration == null) {
            throw new UnmarshalException("Failed to load configuration. Make sure that " + file.getAbsolutePath() + " exists and is configured correctly");
        }
        return configuration;
    }
}

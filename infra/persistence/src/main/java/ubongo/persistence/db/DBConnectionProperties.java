package ubongo.persistence.db;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "db-connection")
@XmlAccessorType(XmlAccessType.FIELD)
public class DBConnectionProperties {

    @XmlElement private String host;
    @XmlElement private int port;
    @XmlElement private String schema;
    @XmlElement private String user;
    @XmlElement private String password;

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getSchema() {
        return schema;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

}

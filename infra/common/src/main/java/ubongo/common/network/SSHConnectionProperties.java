package ubongo.common.network;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "ssh-connection")
@XmlAccessorType(XmlAccessType.FIELD)
public class SSHConnectionProperties {

    @XmlElement private String host;
    @XmlElement private int port;
    @XmlElement private String user;
    @XmlElement private String keyFilePath;
    @XmlElement private String password;

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUser() {
        return user;
    }

    public String getKeyFilePath() {
        return keyFilePath;
    }

    public String getPassword() {
        return password;
    }
}

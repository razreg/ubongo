package ubongo.common.network;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class SSHConnection {

    private static Logger logger = LogManager.getLogger(SSHConnection.class);

    public static Session establish(SSHConnectionProperties sshProperties) throws JSchException {
        if (sshProperties.getKeyFilePath() != null) {
            return establishWithKey(
                    sshProperties.getHost(),
                    sshProperties.getPort(),
                    sshProperties.getUser(),
                    sshProperties.getKeyFilePath()
            );
        } else {
            return establishWithPassword(
                    sshProperties.getHost(),
                    sshProperties.getPort(),
                    sshProperties.getUser(),
                    sshProperties.getPassword()
            );
        }
    }

    public static Session establishWithPassword(String sshHost, int sshPort, String user, String password) throws JSchException {
        Session session;
        JSch jsch = new JSch();
        try {
            session = jsch.getSession(user, sshHost, sshPort);
            session.setPassword(password);
        }
        catch (JSchException e) {
            logger.error("SSH connection attempt to host: " + sshHost + ":" + sshPort + " failed");
            throw e;
        }
        return connect(session, sshHost, sshPort);
    }

    public static Session establishWithKey(String sshHost, int sshPort, String user, String keyFilePath) throws JSchException {
        File keyFile = new File(keyFilePath);
        if (!keyFile.exists()) {
            String errorMsg = "Could not find SSH public key file in path: " + keyFilePath;
            logger.info(errorMsg);
            throw new JSchException(errorMsg);
        }
        Session session;
        JSch jsch = new JSch();
        try {
            jsch.addIdentity(keyFile.getAbsolutePath());
            session = jsch.getSession(user, sshHost, sshPort);
        }
        catch (JSchException e) {
            logger.error("SSH connection attempt to host: " + sshHost + ":" + sshPort + " failed");
            throw e;
        }
        return connect(session, sshHost, sshPort);
    }

    private static Session connect(Session session, String sshHost, int sshPort) throws JSchException {
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("ConnectionAttempts", "3");
        try {
            session.connect();
        }
        catch (JSchException e) {
            logger.error("SSH connection attempt to host: " + sshHost + ":" + sshPort + " failed");
            throw e;
        }
        logger.info("Connected to: " + sshHost + ":" + sshPort + " via SSH");
        return session;
    }

}

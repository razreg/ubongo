package ubongo.persistence.db;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.persistence.exceptions.DBProxyException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SQLExceptionHandler {

    public static final Logger logger = LogManager.getLogger(SQLExceptionHandler.class);

    public static final boolean THROW = false;
    public static final boolean RETRY = true;

    private DBProxy dbProxy;

    public SQLExceptionHandler(DBProxy dbProxy) {
        this.dbProxy = dbProxy;
    }

    public boolean isRecoverable(SQLException e) {
        if (e instanceof SQLTransientException) {
            return RETRY;
        }
        if (e instanceof SQLRecoverableException) {
            try {
                // try to recover by restarting the connection
                dbProxy.disconnect();
                try {
                    Thread.sleep(100); // give the system a bit of time
                } catch (InterruptedException ie) {
                    // ignore
                }
                dbProxy.connect();
                return RETRY;
            } catch (DBProxyException dbe) {
                // ignore and throw at the end
            }
        }
        if (e instanceof SQLClientInfoException) {
            if (logger.isDebugEnabled()) {
                logger.debug("Client info properties could not be set on the database connection:\n" +
                        failedClientProps(((SQLClientInfoException) e).getFailedProperties()));
            }
        }
        // other possiblities: SQLNonTransientException, SerialException, BatchUpdateException or other
        logSqlException(e);
        return THROW;
    }

    private static String failedClientProps(Map<String, ClientInfoStatus> props) {
        List<String> messages = new ArrayList<>();
        props.entrySet().stream().forEach(entry ->
                messages.add("\t" + entry.getKey() + ": " + translateClientInfoStatus(entry.getValue())));
        return StringUtils.join(messages, "\n");
    }

    private static String translateClientInfoStatus(ClientInfoStatus status) {
        switch (status) {
            case REASON_UNKNOWN_PROPERTY: return "Unknown property";
            case REASON_VALUE_INVALID: return "Invalid value";
            case REASON_VALUE_TRUNCATED: return "Value too large";
            default: return "Unknown reason";
        }
    }

    private void logSqlException(SQLException e) {
        logger.error("SQLException details:" +
                "\n\tSQLException: " + e.getMessage() +
                "\n\tSQLState: " + e.getSQLState() +
                "\n\tVendorError: " + e.getErrorCode(), e);
    }

}

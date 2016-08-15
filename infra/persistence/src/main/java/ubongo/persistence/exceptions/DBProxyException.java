package ubongo.persistence.exceptions;

import ubongo.persistence.PersistenceException;

public class DBProxyException extends PersistenceException {

    public DBProxyException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public DBProxyException(String message) {
        super(message);
    }
}

package ubongo.persistence.exceptions;

import ubongo.persistence.PersistenceException;

public class UnitFetcherException extends PersistenceException {

    public UnitFetcherException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public UnitFetcherException(String message) {
        super(message);
    }
}

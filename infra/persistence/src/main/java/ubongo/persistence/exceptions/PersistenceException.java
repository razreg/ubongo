package ubongo.persistence.exceptions;

public class PersistenceException extends Exception {

    public PersistenceException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public PersistenceException(String message) {
        super(message);
    }

}

package ubongo.server.exceptions;

public class SynchronizationException extends ExecutionException {

    public SynchronizationException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public SynchronizationException(String message) {
        super(message);
    }

}

package ubongo.server.exceptions;

public class QueueManagementException extends ExecutionException {

    public QueueManagementException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public QueueManagementException(String message) {
        super(message);
    }
}

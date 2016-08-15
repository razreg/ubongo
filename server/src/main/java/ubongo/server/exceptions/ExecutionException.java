package ubongo.server.exceptions;

/**
 * An Exception class for exceptions stemming from any dispatcher related problems
 * (e.g., invalid flow received from user, failure to send execution request to machine).
 */
public class ExecutionException extends Exception {

    public ExecutionException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public ExecutionException(String message) {
        super(message);
    }

}

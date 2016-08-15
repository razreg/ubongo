package ubongo.common.exceptions;


public class NetworkException extends Exception {

    public NetworkException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public NetworkException(String message) {
        super(message);
    }
}

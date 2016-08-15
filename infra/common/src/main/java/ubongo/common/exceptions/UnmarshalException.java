package ubongo.common.exceptions;


public class UnmarshalException extends Exception{

    public UnmarshalException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public UnmarshalException(String message) {
        super(message);
    }
}

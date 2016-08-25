package ubongo.rest;

public class UbongoHttpException extends Exception {

    private int status;

    public UbongoHttpException(int statusCode, String message) {
        super(message);
        status = statusCode;
    }

    public UbongoHttpException(int statusCode, String message, Throwable t) {
        super(message, t);
        status = statusCode;
    }

    public int getStatus() {
        return status;
    }

}

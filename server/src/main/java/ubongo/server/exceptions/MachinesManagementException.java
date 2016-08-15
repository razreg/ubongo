package ubongo.server.exceptions;

public class MachinesManagementException extends ExecutionException {

    public MachinesManagementException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public MachinesManagementException(String message) {
        super(message);
    }
}
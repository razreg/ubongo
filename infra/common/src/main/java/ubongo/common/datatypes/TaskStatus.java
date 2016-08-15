package ubongo.common.datatypes;

public enum TaskStatus {

    CREATED ("Created"),
    NEW ("New"),
    PENDING ("Pending"),
    PROCESSING ("Processing"),
    COMPLETED ("Completed"),
    FAILED ("Failed"),
    CANCELED ("Canceled"),
    STOPPED ("Stopped"),
    STOPPED_FAILURE ("Stop_Failed"),
    ON_HOLD ("On_Hold");

    private final String name;
    private static final TaskStatus[] finalStatuses =
            {COMPLETED, FAILED, CANCELED, STOPPED, STOPPED_FAILURE};

    TaskStatus(String name) {
        this.name = name;
    }

    public static TaskStatus[] getFinalStatuses() {
        return finalStatuses;
    }

    @Override
    public String toString() {
        return this.name;
    }
}

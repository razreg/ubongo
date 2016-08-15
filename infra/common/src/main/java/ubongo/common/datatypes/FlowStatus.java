package ubongo.common.datatypes;

public enum FlowStatus {

    NEW ("New"),
    IN_PROGRESS ("In_Progress"),
    COMPLETED ("Completed"),
    STUCK ("Stuck"),
    CANCELED ("Canceled"),
    STOPPED ("Stopped"),
    ERROR ("Error");

    private final String name;

    FlowStatus(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }

}

package ubongo.common.constants;

public class SystemConstants {

    public static final int NETWORK_RETRIES = 5;
    public static final int SLEEP_BETWEEN_NETWORK_RETRIES = 1000;

    public static final String UBONGO_RABBIT_TASKS_QUEUE = "ubongo_tasks";
    public static final String UBONGO_RABBIT_KILL_TASKS_QUEUE = "ubongo_kill_requests";
    public static final String UBONGO_SERVER_TASKS_STATUS_QUEUE = "ubongo_tasks_status";

}

package ubongo.persistence.db;

public class DBConstants {

    public final static String DEBUG_PREFIX = "zz_debug_";

    public final static String QUERY_GET_FLOW_TASKS = "get_flow_tasks";
    public final static String QUERY_GET_NEW_TASKS = "get_new_tasks";
    public final static String QUERY_GET_TASK_BY_ID = "get_task_by_id";
    public final static String QUERY_GET_ALL_TASKS = "get_all_tasks";
    public final static String QUERY_GET_ANALYSIS_NAMES = "get_analysis_names";
    public final static String QUERY_GET_UNITS = "get_units";
    public final static String QUERY_UPDATE_TASK_STATUS = "update_task_status";
    public final static String QUERY_CREATE_ANALYSIS = "create_analysis";
    public final static String QUERY_CREATE_FLOW = "create_flow";
    public final static String QUERY_START_FLOW = "start_flow";
    public final static String QUERY_GET_ALL_FLOWS = "get_all_flows";
    public final static String QUERY_UPDATE_FLOW_STATUS = "update_flow_status";
    public final static String QUERY_CLEAR_TABLES = "clear_tables";
    public final static String QUERY_RESUME_TASK = "resume_task";
    public final static String QUERY_CREATE_REQUEST = "create_request";
    public final static String QUERY_GET_NEW_REQUESTS = "get_new_requests";
    public final static String QUERY_UPDATE_REQUEST_STATUS = "update_request_status";
    public final static String QUERY_SAVE_MACHINES = "save_machines";
    public final static String QUERY_GET_MACHINES = "get_machines";
    public final static String QUERY_UPDATE_MACHINES = "update_machine";
    public final static String QUERY_CHANGE_MACHINE_ACTIVITY = "change_machine_activity";

    public final static String TASKS_TABLE_NAME = "tasks";
    public final static String TASKS_TASK_ID = "task_id";
    public final static String TASKS_FLOW_ID = "flow_id";
    public final static String TASKS_SERIAL_NUM = "serial_in_flow";
    public final static String TASKS_TASK_STATUS = "status";
    public final static String TASKS_UNIT_ID = "unit_id";
    public final static String TASKS_UNIT_PARAMS = "unit_params";
    public final static String TASKS_STUDY = "study";
    public final static String TASKS_SUBJECT = "subject";
    public final static String TASKS_RUN = "run";
    public final static String TASKS_MACHINE_ID = "machine_id";

    public final static String FLOWS_TABLE_NAME = "flows";
    public final static String FLOWS_FLOW_ID = "flow_id";
    public final static String FLOWS_STUDY_NAME = "study_name";
    public final static String FLOWS_INSERTION_TIME = "insertion_time";
    public final static String FLOWS_STATUS = "status";

    public final static String UNITS_TABLE_NAME = "units";
    public final static String UNITS_ANALYSIS_NAME = "analysis_name";
    public final static String UNITS_UNIT_ID = "external_unit_id";

    public final static String REQUESTS_TABLE_NAME = "requests";
    public final static String REQUESTS_ID = "id";
    public final static String REQUESTS_ENTITY_ID = "entity_id";
    public final static String REQUESTS_ACTION = "action";
    public final static String REQUESTS_STATUS = "status";

    public final static String MACHINES_TABLE_NAME = "machines";
    public final static String MACHINES_ID = "id";
    public final static String MACHINES_HOST = "host";
    public final static String MACHINES_DESCRIPTION = "description";
    public final static String MACHINES_ACTIVE = "active";
    public final static String MACHINES_CONNECTED = "connected";
    public final static String MACHINES_LAST_HEARTBEAT = "last_heartbeat";

}

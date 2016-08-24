package ubongo.persistence.db;

import com.google.gson.JsonParseException;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.Utils;
import ubongo.common.datatypes.*;
import ubongo.common.datatypes.unit.Unit;
import ubongo.common.datatypes.unit.UnitParameter;
import ubongo.common.network.SSHConnection;
import ubongo.common.network.SSHConnectionProperties;
import ubongo.persistence.UnitFetcher;
import ubongo.persistence.exceptions.DBProxyException;
import ubongo.persistence.exceptions.UnitFetcherException;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DBProxy {

    private static Logger logger = LogManager.getLogger(DBProxy.class);

    private Session sshSession;
    private SSHConnectionProperties sshProperties;
    private boolean useSSH;
    private Map<Integer, Machine> machines;
    private Connection connection;
    private DBConnectionProperties dbProperties;
    private int localPort;
    private UnitFetcher unitFetcher;
    private QueriesProvider queriesProvider;

    private boolean debug = false;

    public DBProxy(UnitFetcher unitFetcher, DBConnectionProperties dbConnectionProperties,
                   List<Machine> machines, String queriesPath, boolean debug) {
        this.queriesProvider = new QueriesProvider(queriesPath);
        this.dbProperties = dbConnectionProperties;
        this.unitFetcher = unitFetcher;
        this.machines = machines == null ? new HashMap<>() : machines.stream()
                .collect(Collectors.toMap(Machine::getId, Function.identity()));
        this.useSSH = false;
        this.debug = debug;
    }

    public DBProxy(UnitFetcher unitFetcher, DBConnectionProperties dbConnectionProperties,
                   SSHConnectionProperties sshConnectionProperties, List<Machine> machines,
                   String queriesPath, boolean debug) {
        this(unitFetcher, dbConnectionProperties, machines, queriesPath, debug);
        this.sshProperties = sshConnectionProperties;
        this.useSSH = true;
    }

    public Void start() throws DBProxyException {
        connect();
        return null;
    }

    public void connect() throws DBProxyException {
        try {
            if (useSSH && (sshSession == null || !sshSession.isConnected())) {
                try {
                    sshSession = SSHConnection.establish(sshProperties);
                    localPort = getFreeLocalPort();
                    logger.info("Setting SSH Tunneling to remote DB (" + dbProperties.getHost() + ":" + dbProperties.getPort()
                            + ") using local port " + localPort + "...");
                    sshSession.setPortForwardingL(localPort, dbProperties.getHost(), dbProperties.getPort());
                } catch (JSchException e) {
                    String errorMsg = "Failed to establish SSH connection to the database";
                    logger.error(errorMsg);
                    throw new DBProxyException(errorMsg, e);
                }
            }
            if (connection == null || connection.isClosed()) {
                logger.info("Establishing connection to " + getUrl() + " with user " + getUser() + "...");
                String driver = "com.mysql.jdbc.Driver";
                try {
                    Class.forName(driver);
                    connection = DriverManager.getConnection(getActualUrl(), getUser(), dbProperties.getPassword());
                } catch (SQLException e) {
                    String errorMsg = String.format("Failed to connect to the database (url: %s; user: %s)", getUrl(), getUser());
                    throw new DBProxyException(errorMsg, e);
                } catch (ClassNotFoundException e) {
                    throw new DBProxyException("Database connection cannot be established. " +
                            "MySQL JDBC driver class (" + driver + ") was not found", e);
                }
                logger.info("Connected to DB at " + getUrl());
            }
        } catch (SQLException e) {
            String errorMsg =
                    String.format("Failed to connect to the database (url: %s; user: %s).", getUrl(), getUser());
            throw new DBProxyException(errorMsg, e);
        }
    }

    public Void disconnect() throws DBProxyException {
        try {
            boolean alreadyClosed = true;
            if (connection != null && !connection.isClosed()) {
                alreadyClosed = false;
                logger.info("Closing connection to " + getUrl() + "...");
                connection.close();
            }
            if (sshSession != null && sshSession.isConnected()) {
                alreadyClosed = false;
                sshSession.disconnect();
            }
            if (!alreadyClosed) {
                logger.info("Successfully closed database connection via SSH tunneling");
            }
        } catch (SQLException e) {
            String errorMsg =
                    String.format("Failed to disconnect from the database (url: %s; user: %s).", getUrl(), getUser());
            throw new DBProxyException(errorMsg, e);
        }
        return null;
    }

    public Void performCleanup() throws DBProxyException {
        connect();
        String tasksTableName = getTableName(DBConstants.TASKS_TABLE_NAME);
        String requestsTableName = getTableName(DBConstants.REQUESTS_TABLE_NAME);
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_CLEANUP)
                    .replace("$tasksTable", tasksTableName)
                    .replace("$requestsTable", requestsTableName);
            PreparedStatement statement = connection.prepareStatement(sql);
            executeUpdate(statement);
        } catch (SQLException e) {
            String errorMsg = "Failed to cleanup the DB";
            throw new DBProxyException(errorMsg, e);
        }
        return null;
    }

    /**
     * updates the given task's status in the DB (based on id)
     * A task may not change from Processing to Pending (it must be cancelled or completed beforehand)
     * @param task to updateTaskStatus in DB.
     *             If the task ID cannot be found in the DB, this method does nothing
     */
    public void updateStatus(Task task) throws DBProxyException {
        connect();
        if (logger.isDebugEnabled()) {
            logger.debug("Updating status in DB to " + task.getStatus() + " for taskId=" +
                    task.getId());
        }
        Task taskInDb = getTask(task.getId());
        for (TaskStatus status : TaskStatus.getFinalStatuses()) {
            if (taskInDb.getStatus() == status) {
                logger.warn("Received request to update status of task (taskId=" + task.getId() + ") from "
                        + taskInDb.getStatus() + " to " + task.getStatus() + ". Request denied.");
                return;
            }
        }
        String tableName = getTableName(DBConstants.TASKS_TABLE_NAME);
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_UPDATE_TASK_STATUS)
                    .replace("$tasksTable", tableName);
            PreparedStatement statement = connection.prepareStatement(sql);
            String status = getStatusString(task.getStatus());
            // if the current status is Processing, don't update to Pending -
            // this situation may be caused by threads synchronization issues and is not desired
            // status to set if the current status is Processing
            if (task.getStatus() == TaskStatus.PENDING) {
                statement.setString(1, getStatusString(TaskStatus.PROCESSING));
            } else {
                statement.setString(1, status);
            }
            statement.setString(2, status); // status to set if the current status is not Processing
            // update execution time and machine if relevant
            if (task.getStatus() == TaskStatus.PROCESSING && task.getMachine() != null) {
                statement.setInt(3, task.getMachine().getId());
            } else {
                statement.setNull(3, Types.INTEGER);
            }
            statement.setInt(4, task.getId()); // id of task to update
            executeUpdate(statement);
        } catch (SQLException e) {
            String errorMsg = "Failed to update task's status in DB (taskId="
                    + task.getId() + ", newStatus=" + task.getStatus() + ")";
            throw new DBProxyException(errorMsg, e);
        }
        updateFlowStatus(task.getId());
    }

    public void updateStatus(Collection<Task> tasks) throws DBProxyException {
        for (Task task : tasks) {
            updateStatus(task);
        }
    }

    public void createAnalysis(String analysisName, List<Unit> units) throws DBProxyException {
        connect();
        String unitsTableName = getTableName(DBConstants.UNITS_TABLE_NAME);
        try {
            String values = getUnitsAsValueList(analysisName, units);
            if (values == null) {
                String errMsg = "System tried to add an empty list of units to the database";
                logger.warn(errMsg);
                throw new DBProxyException(errMsg);
            }
            String sql = queriesProvider.getQuery(DBConstants.QUERY_CREATE_ANALYSIS)
                    .replace("$unitsTable", unitsTableName)
                    .replace("$values", values);
            PreparedStatement statement = connection.prepareStatement(sql);
            executeUpdate(statement);
        } catch (SQLException e) {
            String errorMsg = "Failed to add analysis to DB";
            throw new DBProxyException(errorMsg, e);
        }
    }

    public List<Unit> getAnalysis(String analysisName) throws DBProxyException, UnitFetcherException {
        connect();
        List<Unit> units = new ArrayList<>();
        String tableName = getTableName(DBConstants.UNITS_TABLE_NAME);
        String errorMsg = "Failed to retrieve units from DB";
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_GET_UNITS)
                    .replace("$unitsTable", tableName);
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, analysisName);
            ResultSet resultSet = executeQuery(statement);
            while (resultSet.next()) {
                int unitId = resultSet.getInt(DBConstants.UNITS_UNIT_ID);
                units.add(unitFetcher.getUnit(unitId));
            }
        } catch (SQLException e) {
            throw new DBProxyException(errorMsg, e);
        }
        return units;
    }

    public List<String> getAnalysisNames(int limit) throws DBProxyException {
        connect();
        List<String> analysisNames = new ArrayList<>();
        String tableName = getTableName(DBConstants.UNITS_TABLE_NAME);
        String errorMsg = "Failed to retrieve analyses from DB";
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_GET_ANALYSIS_NAMES)
                    .replace("$unitsTable", tableName);
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setInt(1, limit);
            ResultSet resultSet = executeQuery(statement);
            while (resultSet.next()) {
                analysisNames.add(resultSet.getString(DBConstants.UNITS_ANALYSIS_NAME));
            }
        } catch (SQLException e) {
            throw new DBProxyException(errorMsg, e);
        }
        return analysisNames;
    }

    public List<Machine> getAllMachines(boolean includeServer) throws DBProxyException {
        connect();
        List<Machine> machines = new ArrayList<>();
        String tableName = getTableName(DBConstants.MACHINES_TABLE_NAME);
        String errorMsg = "Failed to retrieve machines from DB";
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_GET_MACHINES)
                    .replace("$machinesTable", tableName);
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = executeQuery(statement);
            while (resultSet.next()) {
                machines.add(machineFromResultSet(resultSet));
            }
        } catch (SQLException e) {
            throw new DBProxyException(errorMsg, e);
        }
        if (!includeServer) {
            machines = machines.stream()
                    .filter(m -> m.getId() != DBConstants.SERVER_ID)
                    .collect(Collectors.toList());
        }
        return machines;
    }

    public void updateMachine(Machine machine) throws DBProxyException {
        connect();
        String machinesTableName = getTableName(DBConstants.MACHINES_TABLE_NAME);
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_UPDATE_MACHINES)
                    .replace("$machinesTable", machinesTableName);
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setBoolean(1, machine.isConnected());
            statement.setInt(2, machine.getId());
            executeUpdate(statement);
        } catch (SQLException e) {
            String errorMsg = "Failed to update machine in DB.";
            throw new DBProxyException(errorMsg, e);
        }
    }

    public void saveMachines(List<Machine> machines) throws DBProxyException {
        connect();
        String machinesTableName = getTableName(DBConstants.MACHINES_TABLE_NAME);
        try {
            String values = getMachinesAsValueList(machines);
            if (values == null) {
                String errMsg = "System tried to add an empty list of machines to the database.";
                logger.warn(errMsg);
                throw new DBProxyException(errMsg);
            }
            String sql = queriesProvider.getQuery(DBConstants.QUERY_SAVE_MACHINES)
                    .replace("$machinesTable", machinesTableName)
                    .replace("$values", values);
            PreparedStatement statement = connection.prepareStatement(sql);
            executeUpdate(statement);
        } catch (SQLException e) {
            String errorMsg = "Failed to add machines to DB.";
            throw new DBProxyException(errorMsg, e);
        }
    }

    public void changeMachineActivityStatus(int machineId, boolean activate) throws DBProxyException {
        connect();
        String machinesTableName = getTableName(DBConstants.MACHINES_TABLE_NAME);
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_CHANGE_MACHINE_ACTIVITY)
                    .replace("$machinesTable", machinesTableName);
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setBoolean(1, activate);
            statement.setInt(2, machineId);
            executeUpdate(statement);
        } catch (SQLException e) {
            String errorMsg = "Failed to " + (activate ? "" : "de") + "activate machine with ID = " + machineId;
            throw new DBProxyException(errorMsg, e);
        }
    }

    public int countRequests(Timestamp t) throws DBProxyException {
        connect();
        String requestsTableName = getTableName(DBConstants.REQUESTS_TABLE_NAME);
        String errorMsg = "Failed to retrieve requests from DB.";
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_COUNT_REQUESTS)
                    .replace("$requestsTable", requestsTableName);
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setTimestamp(1, t);
            ResultSet resultSet = executeQuery(statement);
            if (resultSet.next()) {
                return resultSet.getInt(DBConstants.REQUESTS_COUNT);
            } else {
                throw new DBProxyException("Failed to count requests: query did not return any result.");
            }
        } catch (SQLException e) {
            throw new DBProxyException(errorMsg, e);
        }
    }

    public List<ExecutionRequest> getAllRequests(int limit) throws DBProxyException {
        connect();
        List<ExecutionRequest> requests = new ArrayList<>();
        String requestsTableName = getTableName(DBConstants.REQUESTS_TABLE_NAME);
        String errorMsg = "Failed to retrieve requests from DB.";
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_GET_ALL_REQUESTS)
                    .replace("$requestsTable", requestsTableName);
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setInt(1, limit);
            ResultSet resultSet = executeQuery(statement);
            while (resultSet.next()) {
                requests.add(requestFromResultSet(resultSet));
            }
        } catch (SQLException e) {
            throw new DBProxyException(errorMsg, e);
        }
        return requests;
    }

    public List<ExecutionRequest> getNewRequests() throws DBProxyException {
        connect();
        List<ExecutionRequest> requests = new ArrayList<>();
        String requestsTableName = getTableName(DBConstants.REQUESTS_TABLE_NAME);
        String errorMsg = "Failed to retrieve requests from DB.";
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_GET_NEW_REQUESTS)
                    .replace("$requestsTable", requestsTableName);
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = executeQuery(statement);
            while (resultSet.next()) {
                requests.add(requestFromResultSet(resultSet));
            }
        } catch (SQLException e) {
            throw new DBProxyException(errorMsg, e);
        }
        return requests;
    }

    public void updateRequestStatus(ExecutionRequest request) throws DBProxyException {
        connect();
        String requestsTableName = getTableName(DBConstants.REQUESTS_TABLE_NAME);
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_UPDATE_REQUEST_STATUS)
                    .replace("$requestsTable", requestsTableName);
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, request.getStatus().toString());
            statement.setInt(2, request.getId());
            executeUpdate(statement);
        } catch (SQLException e) {
            String errorMsg = "Failed to update request (id=" + request.getId()
                    + ") status in DB to " + request.getStatus();
            throw new DBProxyException(errorMsg, e);
        }
    }

    public void saveRequest(ExecutionRequest request) throws DBProxyException {
        connect();
        String requestsTableName = getTableName(DBConstants.REQUESTS_TABLE_NAME);
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_CREATE_REQUEST)
                    .replace("$requestsTable", requestsTableName);
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setInt(1, request.getEntityId());
            statement.setString(2, request.getAction().toString());
            executeUpdate(statement);
        } catch (SQLException e) {
            String errorMsg = "Failed to add request to DB.";
            throw new DBProxyException(errorMsg, e);
        }
    }

    public int createFlow(Context context, List<Task> tasks) throws DBProxyException {
        connect();
        String tasksTableName = getTableName(DBConstants.TASKS_TABLE_NAME);
        String flowsTableName = getTableName(DBConstants.FLOWS_TABLE_NAME);
        for (Task task : tasks) {
            task.setStatus(TaskStatus.CREATED);
        }
        try {
            String values = getTasksAsValueList(tasks);
            if (values == null) {
                String errMsg = "System tried to add an empty list of tasks to the database.";
                logger.warn(errMsg);
                throw new DBProxyException(errMsg);
            }
            String sql = queriesProvider.getQuery(DBConstants.QUERY_CREATE_FLOW)
                    .replace("$flowsTable", flowsTableName)
                    .replace("$tasksTable", tasksTableName)
                    .replace("$values", values);
            PreparedStatement statement =
                    connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, context.getStudy());
            statement.setString(2, context.getSubject());
            statement.setString(3, context.getRun());
            executeUpdate(statement);
            ResultSet results = statement.getGeneratedKeys();
            results.next();
            return results.getInt(1);
        } catch (SQLException e) {
            String errorMsg = "Failed to add tasks to DB.";
            throw new DBProxyException(errorMsg, e);
        }
    }

    public void startFlow(int flowId) throws DBProxyException {
        connect();
        String tasksTableName = getTableName(DBConstants.TASKS_TABLE_NAME);
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_START_FLOW)
                    .replace("$tasksTable", tasksTableName);
            PreparedStatement statement =
                    connection.prepareStatement(sql);
            statement.setInt(1, flowId);
            int affectedRows = executeUpdate(statement);
            if (affectedRows <= 0) {
                throw new DBProxyException("Failed to start flow: there were no tasks in status 'CREATED' in flow=" + flowId);
            }
        } catch (SQLException e) {
            String errorMsg = "Failed to start flow in DB.";
            throw new DBProxyException(errorMsg, e);
        }
    }

    public List<Task> cancelFlow(int flowId) throws DBProxyException {

        List<Task> tasks = getTasks(flowId);
        if (tasks == null || tasks.isEmpty()) {
            throw new DBProxyException("Could not find tasks with flowId=" + flowId +
                    ": no such flow in DB.");
        }
        List<Task> tasksToCancel = tasks.stream().filter(t -> {
            if (t.getStatus() == TaskStatus.PROCESSING) return false;
            for (TaskStatus status : TaskStatus.getFinalStatuses())
                if (status == t.getStatus()) return false;
            return true;
        }).collect(Collectors.toList());
        for (Task task : tasksToCancel) {
            task.setStatus(TaskStatus.CANCELED);
            cancelTask(task);
        }
        return tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.PROCESSING)
                .collect(Collectors.toList());
    }

    public boolean cancelTask(Task task) throws DBProxyException {
        Task taskFromDb = getTask(task.getId());
        TaskStatus[] finalStatuses = TaskStatus.getFinalStatuses();
        if (taskFromDb.getStatus() == TaskStatus.PROCESSING) {
            logger.info("Tried to cancel task (taskId=" + task.getId() + ") but the task is already executing.");
            return false;
        }
        for (TaskStatus status : finalStatuses) {
            if (taskFromDb.getStatus() == status) {
                return true;
            }
        }
        task.setStatus(TaskStatus.CANCELED);
        updateStatus(task);
        return true;
    }

    public void insertContextToTask(Task originalTask, List<Task> replacements) throws DBProxyException {
        connect();
        String tasksTableName = getTableName(DBConstants.TASKS_TABLE_NAME);
        for (Task task : replacements) {
            task.setStatus(TaskStatus.NEW);
            task.setFlowId(originalTask.getFlowId());
        }
        try {
            String values = getTasksAsValueList(replacements, true);
            if (values == null) {
                String errMsg = "System tried to add an empty list of tasks to the database.";
                logger.warn(errMsg);
                throw new DBProxyException(errMsg);
            }
            String sql = queriesProvider.getQuery(DBConstants.QUERY_INSERT_CONTEXT_TO_TASKS)
                    .replace("$tasksTable", tasksTableName)
                    .replace("$values", values);
            PreparedStatement statement =
                    connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, originalTask.getId());
            executeUpdate(statement);
        } catch (SQLException e) {
            String errorMsg = "Failed to add tasks to DB.";
            throw new DBProxyException(errorMsg, e);
        }
    }

    public Task getTask(int id) throws DBProxyException {
        List<Task> tasks = getTasks(DBConstants.QUERY_GET_TASK_BY_ID, id);
        if (tasks.size() == 0) {
            throw new DBProxyException("No task in the DB match the given id (" + id + ").");
        }
        return tasks.get(0);
    }

    public List<Task> getProcessingTasks() throws DBProxyException {
        return getTasks(DBConstants.QUERY_GET_PROCESSING_TASKS);
    }

    public List<Task> getNewTasks() throws DBProxyException {
        return getTasks(DBConstants.QUERY_GET_NEW_TASKS);
    }

    public List<Task> getTasks(int flowId) throws DBProxyException {
        return getTasks(DBConstants.QUERY_GET_FLOW_TASKS, flowId);
    }

    public List<Task> getAllTasks(int limit) throws DBProxyException {
        return getTasks(DBConstants.QUERY_GET_ALL_TASKS, limit);
    }

    public List<FlowData> getAllFlows(int limit) throws DBProxyException {
        connect();
        List<FlowData> flows = new ArrayList<>();
        String flowsTableName = getTableName(DBConstants.FLOWS_TABLE_NAME);
        String errorMsg = "Failed to retrieve flows from DB.";
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_GET_ALL_FLOWS)
                    .replace("$flowsTable", flowsTableName);
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setInt(1, limit);
            ResultSet resultSet = executeQuery(statement);
            while (resultSet.next()) {
                flows.add(flowFromResultSet(resultSet));
            }
        } catch (SQLException | JsonParseException e) {
            throw new DBProxyException(errorMsg, e);
        }
        return flows;
    }

    public void resumeTask(int taskId) throws DBProxyException {
        connect();
        if (logger.isDebugEnabled()) {
            logger.debug("Updating status in DB to " + TaskStatus.NEW + " for taskId=" + taskId);
        }
        String tableName = getTableName(DBConstants.TASKS_TABLE_NAME);
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_RESUME_TASK)
                    .replace("$tasksTable", tableName);
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setInt(1, taskId);
            executeUpdate(statement);
        } catch (SQLException e) {
            throw new DBProxyException("Failed to resume task (taskId=" + taskId + ").", e);
        }
        updateFlowStatus(taskId);
    }

    public Void clearAllDebugTables() throws DBProxyException {
        connect();
        String tasksTableName = getTableName(DBConstants.TASKS_TABLE_NAME, true);
        String flowsTableName = getTableName(DBConstants.FLOWS_TABLE_NAME, true);
        String unitsTableName = getTableName(DBConstants.UNITS_TABLE_NAME, true);
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_CLEAR_TABLES)
                    .replace("$tasksTable", tasksTableName)
                    .replace("$flowsTable", flowsTableName)
                    .replace("$unitsTable", unitsTableName);
            PreparedStatement statement =
                    connection.prepareStatement(sql);
            executeUpdate(statement);
        } catch (SQLException e) {
            String errorMsg = "Failed to clear debug tables.";
            throw new DBProxyException(errorMsg, e);
        }
        return null;
    }

    private List<Task> getTasks(String queryName) throws DBProxyException {
        return getTasks(queryName, 0);
    }

    private List<Task> getTasks(String queryName, int arg) throws DBProxyException {
        connect();
        List<Task> tasks = new ArrayList<>();
        String tasksTableName = getTableName(DBConstants.TASKS_TABLE_NAME);
        String flowsTableName = getTableName(DBConstants.FLOWS_TABLE_NAME);
        String errorMsg = "Failed to retrieve tasks from DB.";
        try {
            String sql = queriesProvider.getQuery(queryName)
                    .replace("$flowsTable", flowsTableName)
                    .replace("$tasksTable", tasksTableName);
            PreparedStatement statement = connection.prepareStatement(sql);
            if (queryName.equals(DBConstants.QUERY_GET_FLOW_TASKS) ||
                    queryName.equals(DBConstants.QUERY_GET_TASK_BY_ID)) {
                statement.setInt(1, arg);
            } else if (queryName.equals(DBConstants.QUERY_GET_ALL_TASKS)) {
                statement.setInt(1, arg);
            }
            ResultSet resultSet = executeQuery(statement);
            while (resultSet.next()) {
                tasks.add(taskFromResultSet(resultSet));
            }
        } catch (SQLException | JsonParseException | UnitFetcherException e) {
            throw new DBProxyException(errorMsg, e);
        }
        return tasks;
    }

    private Machine machineFromResultSet(ResultSet resultSet) throws SQLException {
        Machine machine = new Machine();
        machine.setId(resultSet.getInt(DBConstants.MACHINES_ID));
        machine.setHost(resultSet.getString(DBConstants.MACHINES_HOST));
        machine.setDescription(resultSet.getString(DBConstants.MACHINES_DESCRIPTION));
        machine.setActive(resultSet.getBoolean(DBConstants.MACHINES_ACTIVE));
        machine.setConnected(resultSet.getBoolean(DBConstants.MACHINES_CONNECTED));
        machine.setLastHeartbeat(resultSet.getTimestamp(DBConstants.MACHINES_LAST_HEARTBEAT));
        return machine;
    }

    private ExecutionRequest requestFromResultSet(ResultSet resultSet) throws SQLException {
        ExecutionRequest request = new ExecutionRequest();
        request.setId(resultSet.getInt(DBConstants.REQUESTS_ID));
        request.setEntityId(resultSet.getInt(DBConstants.REQUESTS_ENTITY_ID));
        String actionStr = resultSet.getString(DBConstants.REQUESTS_ACTION);
        String statusStr = resultSet.getString(DBConstants.REQUESTS_STATUS);
        request.setAction(actionStr == null ? null : ExecutionRequest.Action.fromString(actionStr));
        request.setStatus(statusStr == null ? null : ExecutionRequest.Status.fromString(statusStr));
        request.setLastUpdated(resultSet.getTimestamp(DBConstants.REQUESTS_LAST_UPDATED));
        request.setCreationTime(resultSet.getTimestamp(DBConstants.REQUESTS_INSERTION_TIME));
        return request;
    }

    private Task taskFromResultSet(ResultSet resultSet) throws SQLException, UnitFetcherException {
        Unit unit = unitFetcher.getUnit(resultSet.getInt(DBConstants.TASKS_UNIT_ID));
        unit.setParameterValues(resultSet.getString(DBConstants.TASKS_UNIT_PARAMS));
        int machineId = resultSet.getInt(DBConstants.TASKS_MACHINE_ID);
        Context context = new Context(
                resultSet.getString(DBConstants.TASKS_STUDY),
                resultSet.getString(DBConstants.TASKS_SUBJECT),
                resultSet.getString(DBConstants.TASKS_RUN)
        );

        return new Task(
                resultSet.getInt(DBConstants.TASKS_TASK_ID),
                resultSet.getInt(DBConstants.TASKS_FLOW_ID),
                resultSet.getInt(DBConstants.TASKS_SERIAL_NUM),
                unit,
                machineId == 0 ? null : machines.get(machineId),
                context,
                TaskStatus.valueOf(resultSet.getString(DBConstants.TASKS_TASK_STATUS).toUpperCase())
        );
    }

    private String getUnitsAsValueList(String analysisName, List<Unit> units) {
        // (analysis_name, serial, external_unit_id)
        List<String> valuesList = new ArrayList<>();
        int serial = 0;
        for (Unit unit : units) {
            valuesList.add(Utils.concatStrings(
                    "('", analysisName, "', ",
                    Integer.toString(serial++), ", ",
                    Integer.toString(unit.getId()) , ")"
            ));
        }
        return StringUtils.join(valuesList, ',');
    }

    /**
     * this method should be called after every task status update. It updates the flow's status according to the
     * status of its constituent tasks.
     * @param taskId is used to find the flowId which corresponds to this task.
     * @throws DBProxyException
     */
    private void updateFlowStatus(int taskId) throws DBProxyException {
        List<Task> flowTasks = getAllTasksWithSameFlowId(taskId);
        if (flowTasks == null || flowTasks.isEmpty()) {
            throw new DBProxyException("Failed to update flow for taskId=" + taskId);
        }
        FlowStatus status = computeFlowStatus(flowTasks);
        updateFlowStatus(flowTasks.get(0).getFlowId(), status);
    }

    private void updateFlowStatus(int flowId, FlowStatus status) throws DBProxyException {
        connect();
        if (logger.isDebugEnabled()) {
            logger.debug("Updating flow status in DB to " + status + " for flowId=" + flowId);
        }
        String tableName = getTableName(DBConstants.FLOWS_TABLE_NAME);
        try {
            String sql = queriesProvider.getQuery(DBConstants.QUERY_UPDATE_FLOW_STATUS)
                    .replace("$flowsTable", tableName);
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setString(1, status.toString());
            statement.setInt(2, flowId);
            executeUpdate(statement);
        } catch (SQLException e) {
            String errorMsg = "Failed to update flow's status in DB (flowId="
                    + flowId + ", newStatus=" + status + ")";
            throw new DBProxyException(errorMsg, e);
        }
    }

    /**
     * Computes the status of the flow corresponding to the given list of tasks, based on their statuses.
     * @param flowTasks composing flow for which we compute the status.
     * @return FlowStatus for this flow.
     */
    private FlowStatus computeFlowStatus(List<Task> flowTasks) {
        FlowStatus flowStatus = FlowStatus.NEW;
        int completedCount = 0;
        boolean cancelled = false;
        loop: for (Task task : flowTasks) {
            cancelled = false;
            switch (task.getStatus()) {
                case PENDING:
                case PROCESSING:
                    if (flowStatus == FlowStatus.NEW) {
                        flowStatus = FlowStatus.IN_PROGRESS;
                    }
                    break;
                case COMPLETED:
                    completedCount++;
                    break;
                case ON_HOLD:
                case FAILED:
                    flowStatus = FlowStatus.STUCK;
                    break loop;
                case STOPPED:
                    flowStatus = FlowStatus.STOPPED;
                    break loop;
                case STOPPED_FAILURE:
                    flowStatus = FlowStatus.ERROR;
                    break loop;
                case CANCELED:
                    cancelled = true;
                    break;
                case CREATED:
                case NEW:
                    break;
            }
        }
        if (cancelled) {
            flowStatus = FlowStatus.CANCELED;
        }
        else if (completedCount == flowTasks.size()) {
            flowStatus = FlowStatus.COMPLETED;
        }
        return flowStatus;
    }

    private FlowData flowFromResultSet(ResultSet resultSet) throws SQLException {
        Context context = new Context();
        context.setStudy(resultSet.getString(DBConstants.FLOWS_STUDY_NAME));
        context.setSubject(resultSet.getString(DBConstants.FLOWS_SUBJECT));
        context.setRun(resultSet.getString(DBConstants.FLOWS_RUN));
        return new FlowData(
                resultSet.getInt(DBConstants.FLOWS_FLOW_ID), context,
                timestampToDate(resultSet.getTimestamp(DBConstants.FLOWS_INSERTION_TIME)),
                FlowStatus.valueOf(resultSet.getString(DBConstants.FLOWS_STATUS).toUpperCase())
        );
    }

    private static Date timestampToDate(Timestamp timestamp) {
        if (timestamp == null) return null;
        long milliseconds = timestamp.getTime() + (timestamp.getNanos() / 1000000);
        return new Date(milliseconds);
    }

    private List<Task> getAllTasksWithSameFlowId(int taskId) throws DBProxyException {
        Task representative = getTask(taskId);
        if (representative == null) {
            throw new DBProxyException("Cannot update flow for taskId=" + taskId +
                    ". Looks like there is no such task in the DB.");
        }
        return getTasks(representative.getFlowId());
    }

    private String getMachinesAsValueList(List<Machine> machines) {
        // (id, host, description, active, connected)
        return StringUtils.join(machines.stream()
                .map(machine -> Utils.concatStrings("(", machine.getId(), ", '",
                        machine.getHost(), "', '", machine.getDescription(),"', ",
                        (machine.isActive() ? "1" : "0"), ", ",
                        (machine.isConnected() ? "1" : "0"), ")"))
                .collect(Collectors.toList()), ',');
    }

    private String getTasksAsValueList(List<Task> tasks) {
        return getTasksAsValueList(tasks, false);
    }

    private String getTasksAsValueList(List<Task> tasks, boolean useTaskSFlowId) {
        // (status, flow_id, serial_in_flow, unit_id, unit_params, subject, run, machine_id)
        List<String> valuesList = new ArrayList<>();
        for (Task task : tasks) {
            Unit unit = task.getUnit();
            Machine machine = task.getMachine();

            // context
            String subject = "NULL";
            String run = "NULL";
            Context context = task.getContext();
            if (context != null) {
                if (context.getSubject() != null) subject = "'" + context.getSubject() + "'";
                if (context.getRun() != null) run = "'" + context.getRun() + "'";
            }

            // we insert a flow to the DB before inserting tasks so LAST_INSERT_ID() returns the flowId
            valuesList.add(Utils.concatStrings(
                    "('", getStatusString(task.getStatus()), "', ",
                    (useTaskSFlowId ? task.getFlowId()+"": "LAST_INSERT_ID()"), ", ",
                    Integer.toString(task.getSerialNumber()), ", ",
                    ((unit == null)?"NULL":Integer.toString(unit.getId())), ", ",
                    ((unit == null)?"NULL": "'" + getParametersJsonString(unit) + "'"), ", ",
                    subject, ", ", run, ", ",
                    ((machine == null)?"NULL": "'" + machine.getId() + "'"), ")"
            ));
        }
        return StringUtils.join(valuesList, ',');
    }

    private String getStatusString(TaskStatus status) {
        return status.toString(); // StringUtils.capitalize(status.toString().toLowerCase());
    }

    private String getParametersJsonString(Unit unit) {
        List<UnitParameter> params = unit.getParameters();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{");
        boolean isFirst = true;
        for (UnitParameter param: params) {
            String nameValuePair = ((isFirst)?"":", ") + "\"" + param.getName() + "\": \"" + param.getValue() + "\"";
            stringBuilder.append(nameValuePair);
            isFirst = false;
        }
        stringBuilder.append("}");
        return stringBuilder.toString();
    }

    private String getUrl() {
        return dbProperties.getHost() + ":" + dbProperties.getPort() + "/" + dbProperties.getSchema();
    }

    private String getActualUrl() {
        String url;
        if (useSSH) {
            url = "jdbc:mysql://localhost:" + localPort + "/" + dbProperties.getSchema();
        } else {
            url = "jdbc:mysql://" + dbProperties.getHost() + ":" + dbProperties.getPort() + "/" + dbProperties.getSchema();
        }
        url += "?allowMultiQueries=true";
        return url;
    }

    private int getFreeLocalPort() {
        ServerSocket serverSocket = null;
        int portNumber;
        try {
            serverSocket = new ServerSocket(0); // get any available port
            portNumber = serverSocket.getLocalPort();
        } catch (IOException e) {
            portNumber = 49999; // fallback port (this line will probably never be executed)
        } finally {
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                // do nothing
            }
        }
        return portNumber;
    }

    private ResultSet executeQuery(PreparedStatement statement) throws SQLException {
        return statement.executeQuery();
    }

    private int executeUpdate(PreparedStatement statement) throws SQLException {
        return statement.executeUpdate();
    }

    private String getUser() {
        return dbProperties.getUser();
    }

    private String getTableName(String baseTableName, boolean debug) {
        return ((!debug)?"":DBConstants.DEBUG_PREFIX) + baseTableName;
    }

    private String getTableName(String baseTableName) {
        return getTableName(baseTableName, debug);
    }
}

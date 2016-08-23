package ubongo.persistence;

import ubongo.common.datatypes.*;
import ubongo.common.datatypes.unit.Unit;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The Persistence module encapsulates the DB, Unit persistence and any other sub-module whose aim is to
 * save and persist the system state.
 */
public interface Persistence {

    /**
     * This method is responsible to start all constituent parts of the Persistence object, such as DB connection.
     * All configuration is expected to be done in the constructor.
     * @throws PersistenceException in case of failure in starting any of the parts,
     * such as establishing DB connection.
     */
    void start() throws PersistenceException;

    /**
     * Stops all parts of the Persistence module. In particular, this procedure closes all connections
     * (e.g. DB, SSH tunneling).
     * @throws PersistenceException in case a connection could not be closed or if stopping the module
     * will cause inconsistent system state.
     */
    void stop() throws PersistenceException;

    /**
     * Adds units to the Units table under the given analysis name.
     * @param analysisName unique identifier.
     * @param units list of units to include in the analysis in order of appearance.
     * @throws PersistenceException if creation was unsuccessful due to DB error or problem with the units.
     */
    void createAnalysis(String analysisName, List<Unit> units) throws PersistenceException;

    /**
     * Retrieves all analysis names from the DB for future queries. Note that Analysis names are unique identifiers.
     * @param limit for query results (corresponds to the SQL word 'LIMIT')
     * @return a list of analysis names.
     * @throws PersistenceException if retrieval from DB failed, most probably because of DB connection problem.
     */
    List<String> getAnalysisNames(int limit) throws PersistenceException;

    /**
     * Retrieves the units comprising an analysis identified by analysisName.
     * @param analysisName unique identifier for a list of units comprising an analysis.
     * @return list of units corresponding to analysisName. If the analysisName does not correspond to an
     * existing analysis in the DB, an empty list of units will be returned.
     * @throws PersistenceException if retrieval from DB failed, most probably because of DB connection problem.
     */
    List<Unit> getAnalysis(String analysisName) throws PersistenceException;

    /**
     * Creates a new flow in Flows table.
     * @param studyName is the name of the study.
     * @param tasks to execute in this flow.
     * @return flowId in DB.
     * @throws PersistenceException if creation failed, usually due to DB connection error or a
     * data problem with tasks.
     */
    int createFlow(String studyName, List<Task> tasks) throws PersistenceException;

    /**
     * Updates the status of the tasks of the given flow to allow the Execution module {@link ubongo.server}
     * to retrieve the flow's tasks and execute them when possible.
     * @param flowId uniquely identifies the flow to start.
     * @throws PersistenceException if update failed, usually due to DB connection error.
     */
    void startFlow(int flowId) throws PersistenceException;

    /**
     * This method updates the status of tasks corresponding to the given flowId such that they will not be executed.
     * However, if tasks were already sent for execution, they can't be canceled and must be stopped/killed -
     * this is a different action that can be performed by the user.
     * Only tasks that have not been executed already and are not executing at the moment of cancellation request
     * will be cancelled by this method. If some of the tasks of a flow were not sent to execution they will be
     * cancelled even if not all flow tasks can be currently cancelled. This means that a call to this function
     * may affect all tasks of a flow, none of the tasks or a strict subset of them.
     * @param flowId to identify the set of tasks to cancl.
     * @return a list of tasks that are currently being processed and therefore cannot be cancelled.
     * This list may be used to send a stop signal to the machines on which the tasks are running.
     * @throws PersistenceException in case a DB error has occurred which caused the cancellation to fail
     * partially or altogether, or if there was no flow corresponding to the given id in the DB.
     * In case of failure, the state of the tasks in the flow is undetermined.
     */
    List<Task> cancelFlow(int flowId) throws PersistenceException;

    /**
     * Retrieves all tasks in the DB which are new and have not been processed yet.
     * This is used internally by the Execution module {@link ubongo.server} which is responsible to
     * execute new tasks.
     * @return list of tasks in status 'New'.
     * @throws PersistenceException if query failed.
     */
    List<Task> getNewTasks() throws PersistenceException;

    /**
     * Updates the status field of the task in the DB according to the status stored in the task object.
     * @param task with taskId and status for update.
     * @throws PersistenceException if update failed, most probably because of a DB connection error.
     */
    void updateTaskStatus(Task task) throws PersistenceException;

    /**
     * This method enables updating the status field of a collection of tasks in one go instead of calling
     * updateTaskStatus on each task {@link #updateTaskStatus(Task)}. The main use of this method is by the Execution
     * module {@link ubongo.server} which needs to update status for tasks that await execution {@see QueueManager}.
     * @param waitingTasks is a collection of tasks, whose status should be updated in the DB.
     * @throws PersistenceException if update failed, most probably because of a DB connection error. In case of such
     * error, there is no guarantee that none of the tasks has been updated - some of the tasks may have already been
     * updated, none of them might have, or all of them.
     */
    void updateTasksStatus(Collection<Task> waitingTasks) throws PersistenceException;

    /**
     * Retrieves task object by task id.
     * @param taskId to identify the task.
     * @return Task corresponding to that flowId. The order of the list corresponds to the order of the tasks
     * in the flow.
     * @throws PersistenceException in case the query failed.
     */
    Task getTask(int taskId) throws PersistenceException;

    /**
     * Retrieves all tasks that belong to a given flow.
     * @param flowId to identify the flow.
     * @return list of Tasks corresponding to that flowId. The order of the list corresponds to the order of the tasks
     * in the flow.
     * @throws PersistenceException in case the query failed.
     */
    List<Task> getTasks(int flowId) throws PersistenceException;

    /**
     * Cancels a task in the DB; namely, changes its status to 'Cancelled'.
     * @param task to cancel.
     * @return true iff the task was cancelled successfully or is in some final state already (e.g. completed or failed).
     * This method returns false in case the task is currently executing and therefore cannot be simply cancelled and
     * must be killed in the machine in order to stop it from running.
     * @throws PersistenceException if the update to the DB was unsuccessful.
     */
    boolean cancelTask(Task task) throws PersistenceException;

    /**
     * Retrieves a unit object from XML serialized file by unitId.
     * @param unitId to retrieve by.
     * @return unit object corresponding to unitId.
     * @throws PersistenceException if unit with unitId does not exist or cannot be deserialized.
     */
    Unit getUnit(int unitId) throws PersistenceException;

    /**
     * Retrieves all units from XML serialized files.
     * @return list of units in the system.
     * @throws PersistenceException if one or more of the units cannot be deserialized (bad format).
     */
    Map<Integer,Unit> getAllUnits() throws PersistenceException;

    /**
     * Retrieves all tasks from the DB upto the given limit (most recent tasks first).
     * @param limit for query results (corresponds to the SQL word 'LIMIT')
     * @return list of all tasks in DB upto limit.
     * @throws PersistenceException if the query has failed.
     */
    List<Task> getAllTasks(int limit) throws PersistenceException;

    /**
     * Retrieves all flows from the DB upto the given limit (most recent flows first).
     * @param limit for query results (corresponds to the SQL word 'LIMIT')
     * @return list of all flows in DB upto limit.
     * @throws PersistenceException if the query has failed.
     */
    List<FlowData> getAllFlows(int limit) throws PersistenceException;

    /**
     * Resumes the given task; namely, changes a task status from 'On Hold' to 'New'.
     * If the old status is not 'On Hold', this method has no effect.
     * @param taskId of task to resume - only taskId is used.
     * @throws PersistenceException if the update has failed.
     */
    void resumeTask(int taskId) throws PersistenceException;

    /**
     * Return a list of ExecutionRequests in last-updated descending order up to the given limit.
     * @param limit corresponds to the SQL LIMIT keyword.
     * @return list of ExecutionRequests.
     * @throws PersistenceException if the query failed.
     */
    List<ExecutionRequest> getAllRequests(int limit) throws PersistenceException;

    /**
     * Returns the number of ExecutionRequests stored in the DB, which were created after the given timestamp.
     * @param t is the request creation time limit.
     * @return number of ExecutionRequests created after t.
     * @throws PersistenceException if the count failed.
     */
    int countRequests(Timestamp t) throws PersistenceException;

    /**
     * Retrieves all execution requests in status 'New' in the DB.
     * @return list of new requests (such as 'run flow', 'cancel task', etc.) to process.
     * @throws PersistenceException if the retrieval from the DB has failed
     */
    List<ExecutionRequest> getNewRequests() throws PersistenceException;

    /**
     * Updates the status of the request corresponding to {@code request.getId()} to {@code request.getStatus()}.
     * @param request to update.
     * @throws PersistenceException if the update to the DB has failed.
     */
    void updateRequestStatus(ExecutionRequest request) throws PersistenceException;

    /**
     * Saves the request object to the DB.
     * @param request to save.
     * @throws PersistenceException if the update to the DB has failed.
     */
    void saveRequest(ExecutionRequest request) throws PersistenceException;

    /**
     * Saves the given machines to the DB - replacing all data in the machines table!
     * @param machines to save.
     * @throws PersistenceException if the update to the DB has failed.
     */
    void saveMachines(List<Machine> machines) throws PersistenceException;

    /**
     * Returns all the machines from the DB.
     * @param includeServer is a flag that tells the DB whether to fetch the server record or not.
     * @return all the machines stored in the DB.
     * @throws PersistenceException if the retrieval from the DB has failed.
     */
    List<Machine> getAllMachines(boolean includeServer) throws PersistenceException;

    /**
     * Replaces the row in thd DB that corresponds to machine with the new value
     * @param machine to update.
     * @throws PersistenceException if the update to the DB has failed.
     */
    void updateMachine(Machine machine) throws PersistenceException;

    /**
     * Changes the machine to active if activate == true and otherwise to inactive.
     * @param machineId to activate/deactivate
     * @param activate flag - true iff this machine needs to be active.
     * @throws PersistenceException if the update to the DB has failed.
     */
    void changeMachineActivityStatus(int machineId, boolean activate) throws PersistenceException;
}

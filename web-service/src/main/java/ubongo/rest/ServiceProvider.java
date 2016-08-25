package ubongo.rest;

import ubongo.common.datatypes.*;
import ubongo.common.datatypes.unit.Unit;
import ubongo.persistence.exceptions.PersistenceException;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * This class is the service provider for the Rest Service. It is used by the latter to execute the actual
 * intercepted operations, while the Rest Service acts as a mediator between the service provider and
 * the service requester (i.e., the user).
 */
public interface ServiceProvider {

    /**
     * Retrieves a list of all the machines in the database, as stored by the server.
     * @return list of all rows of the machines table in the database.
     * @throws PersistenceException if the query has failed.
     */
    List<Machine> getAllMachines() throws PersistenceException;

    /**
     * Executes the flow; changes the status in the DB for the flow's tasks and starts to process them.
     * @param flowId to execute.
     * @throws PersistenceException if the update in the DB has failed.
     */
    void runFlow(int flowId) throws PersistenceException;

    /**
     * Retrieves a list of all the analysis names in the database up to given limit.
     * @param limit is the value to the LIMIT of the SQL query.
     * @return a list of analysis names up to given limit.
     * @throws PersistenceException if the query has failed in the DB.
     */
    List<String> getAllAnalysisNames(int limit) throws PersistenceException;

    /**
     * Creates an analysis in the DB with the given analysis name and list of units.
     * @param analysisName for the analysis.
     * @param units to compose the analysis content.
     * @throws PersistenceException if the update has failed in the DB.
     */
    void createAnalysis(String analysisName, List<Unit> units) throws PersistenceException;

    /**
     * Retrieves the list of units corresponding to the given analysis name.
     * @param analysisName to identify the analysis.
     * @return list of units of the requested analysis.
     * @throws PersistenceException if the query has failed in the DB.
     */
    List<Unit> getAnalysis(String analysisName) throws PersistenceException;

    /**
     * Retrieves all flows stored in the DB.
     * @param limit for the SQL query
     * @return all latest flows in the DB up to limit.
     * @throws PersistenceException if the flows could not be fetched from the DB.
     */
    List<FlowData> getAllFlows(int limit) throws PersistenceException;

    /**
     * Sends a request to stop a task from executing.
     * @param task to stop.
     * @throws PersistenceException if the request fails to be persisted.
     */
    void killTask(Task task) throws PersistenceException;

    /**
     * Creates a flow in the DB.
     * @param context of the flow.
     * @param tasks composed of the units and the flow context.
     * @return flow id to identify the created flow.
     * @throws PersistenceException if the creation of the flow in the DB failed.
     */
    int createFlow(Context context, List<Task> tasks) throws PersistenceException;

    /**
     * Sends a request to cancel a flow in execution or waiting for execution.
     * @param flowId of the flow to cancel.
     * @throws PersistenceException if the request has failed to be persisted.
     */
    void cancelFlow(int flowId) throws PersistenceException;

    /**
     * Retrieves all the tasks from the DB up to given limit.
     * @param limit for SQL query.
     * @return list of tasks from the DB.
     * @throws PersistenceException in case the query failed.
     */
    List<Task> getAllTasks(int limit) throws PersistenceException;

    /**
     * Retrieves all the tasks of the desired flow, ordered by serial number order (ascending).
     * @param flowId to identify flow.
     * @return list of tasks of the given flow.
     * @throws PersistenceException if the query has failed to be executed in the DB.
     */
    List<Task> getTasks(int flowId) throws PersistenceException;

    /**
     * Retrieves a certain task corresponding to the given id.
     * @param taskId to identify task.
     * @return task corresponding to taskId.
     * @throws PersistenceException if the query had failed in the DB.
     */
    Task getTask(int taskId) throws PersistenceException;

    /**
     * Sends a cancelation request fo a given task.
     * @param task to be cancelled.
     * @throws PersistenceException if the request has failed to be persisted.
     */
    void cancelTask(Task task) throws PersistenceException;

    /**
     * Changes the task's status to 'New' so it will be in line for execution again,
     * after it has been stopped, held up, or otherwise stalled.
     * @param task to resume.
     * @throws PersistenceException if the update to the DB has failed.
     */
    void resumeTask(Task task) throws PersistenceException;

    /**
     * Retrieves all the execution units in the system.
     * @return map of units (may be empty if no units re found), where the key is the unit Id.
     * @throws PersistenceException if one or more units is malformed and cannot be read.
     */
    Map<Integer,Unit> getAllUnits() throws PersistenceException;

    /**
     * Changes the activity status of a given machineId to active if activate == true and to inactive otherwise.
     * @param machineId to de/activate.
     * @param activate is a flag that tells whether the machine needs to be activated or deactivated.
     * @throws PersistenceException if the update to the DB has failed.
     */
    void changeMachineActivityStatus(int machineId, boolean activate) throws PersistenceException;

    /**
     * Sends a request to generate a bash script file for the given unit in the server.
     * Requires that the unit has its XML and Matlab files already in place.
     * @param unitId to generate bash for.
     * @throws PersistenceException if the request has failed to be persisted.
     */
    void generateBashFileForUnit(int unitId) throws PersistenceException;

    /**
     * Returns the number of requests in the DB, which are newer than the given timestamp. Used to show notifications.
     * @param t is the timestamp of interest (only later requests will be counted).
     * @return count of requests.
     * @throws PersistenceException if the query had failed in the DB.
     */
    int countRequests(Timestamp t) throws PersistenceException;

    /**
     * Retrieves all the requests in the DB up to given limit ordered by insertion time (most recent first)
     * @param limit for the SQL LIMIT
     * @return list of requests.
     * @throws PersistenceException if the query has failed in the DB.
     */
    List<ExecutionRequest> getAllRequests(int limit) throws PersistenceException;

    void start() throws PersistenceException;

    void stop();

}
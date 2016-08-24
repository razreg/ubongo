package ubongo.rest;

import ubongo.common.datatypes.*;
import ubongo.common.datatypes.unit.Unit;
import ubongo.persistence.exceptions.PersistenceException;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

public interface ServiceProvider {

    List<Machine> getAllMachines() throws PersistenceException;

    /**
     * Executes the flow - changes the status in the DB for the flow tasks and starts to process them.
     * @param flowId to execute.
     * @throws PersistenceException if the update in the DB failed.
     */
    void runFlow(int flowId) throws PersistenceException;

    List<String> getAllAnalysisNames(int limit) throws PersistenceException;

    void createAnalysis(String analysisName, List<Unit> units) throws PersistenceException;

    List<Unit> getAnalysis(String analysisName) throws PersistenceException;

    /**
     * Retrieves all flows stored in the DB.
     * @param limit for the SQL query
     * @return all latest flows in the DB up to limit.
     * @throws PersistenceException if the flows could not be fetched from the DB.
     */
    List<FlowData> getAllFlows(int limit) throws PersistenceException;

    void killTask(Task task) throws PersistenceException;

    /**
     * Creates a flow in the DB.
     * @param context of the flow.
     * @param tasks composed of the units and the flow context.
     * @return flow id.
     * @throws PersistenceException if the creation of the flow in the DB failed.
     */
    int createFlow(Context context, List<Task> tasks) throws PersistenceException;

    void cancelFlow(int flowId) throws PersistenceException;

    /**
     * Retrieves all the tasks from the DB up to given limit.
     * @param limit for SQL query.
     * @return list of tasks from the DB.
     * @throws PersistenceException in case the query failed.
     */
    List<Task> getAllTasks(int limit) throws PersistenceException;

    List<Task> getTasks(int flowId) throws PersistenceException;

    Task getTask(int taskId) throws PersistenceException;

    void cancelTask(Task task) throws PersistenceException;

    // used for re-run a task that has failed (and now on hold), or killed\canceled - or if it is on hold because of previous failed task.
    void resumeTask(Task task) throws PersistenceException;

    /**
     * Retrieves all the execution units in the system.
     * @return map of units (may be empty if no units re found), where the key is the unit Id.
     * @throws PersistenceException if one or more units is malformed and cannot be read.
     */
    Map<Integer,Unit> getAllUnits() throws PersistenceException;

    void changeMachineActivityStatus(int machineId, boolean activate) throws PersistenceException;

    void generateBashFileForUnit(int unitId) throws PersistenceException;

    int countRequests(Timestamp t) throws PersistenceException;

    List<ExecutionRequest> getAllRequests(int limit) throws PersistenceException;

    void start() throws PersistenceException;

    void stop();

}
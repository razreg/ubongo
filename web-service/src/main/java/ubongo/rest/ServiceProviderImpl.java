package ubongo.rest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.datatypes.*;
import ubongo.common.datatypes.unit.Unit;
import ubongo.persistence.Configuration;
import ubongo.persistence.Persistence;
import ubongo.persistence.exceptions.PersistenceException;
import ubongo.persistence.PersistenceImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

public class ServiceProviderImpl implements ServiceProvider {

    private static Logger logger = LogManager.getLogger(ServiceProviderImpl.class);

    private Persistence persistence;
    private String unitsDirPath;

    public ServiceProviderImpl(Configuration configuration, String unitSettingsDirPath,
                               String queriesPath, boolean debug) {
        this.unitsDirPath = unitSettingsDirPath;
        persistence = new PersistenceImpl(unitSettingsDirPath,
                configuration.getDbConnectionProperties(), configuration.getSshConnectionProperties(),
                configuration.getMachines(), queriesPath, debug);
    }

    @Override
    public void start() throws PersistenceException {
        persistence.start();
    }

    @Override
    public void stop() {
        try {
            persistence.stop();
        } catch (PersistenceException e) {
            // do nothing (the error is already logged in persistence)
        }
    }

    @Override
    public List<Machine> getAllMachines() throws PersistenceException {
        return persistence.getAllMachines(true);
    }

    @Override
    public void runFlow(int flowId) throws PersistenceException {
        ExecutionRequest request = new ExecutionRequest(flowId, ExecutionRequest.Action.RUN_FLOW);
        persistence.saveRequest(request);
    }

    @Override
    public List<String> getAllAnalysisNames(int limit) throws PersistenceException {
        return persistence.getAnalysisNames(limit);
    }

    @Override
    public void createAnalysis(String analysisName, List<Unit> units) throws PersistenceException {
        persistence.createAnalysis(analysisName, units);
    }

    @Override
    public List<Unit> getAnalysis(String analysisName) throws PersistenceException {
        return persistence.getAnalysis(analysisName);
    }

    @Override
    public List<FlowData> getAllFlows(int limit) throws PersistenceException {
        return persistence.getAllFlows(limit);
    }

    @Override
    public void killTask(Task task) throws PersistenceException {
        ExecutionRequest request = new ExecutionRequest(task.getId(), ExecutionRequest.Action.KILL_TASK);
        persistence.saveRequest(request);
    }

    @Override
    public int createFlow(String studyName, List<Task> tasks) throws PersistenceException {
        return persistence.createFlow(studyName, tasks);
    }

    @Override
    public void cancelFlow(int flowId) throws PersistenceException {
        ExecutionRequest request = new ExecutionRequest(flowId, ExecutionRequest.Action.CANCEL_FLOW);
        persistence.saveRequest(request);
    }

    @Override
    public List<Task> getAllTasks(int limit) throws PersistenceException {
        return persistence.getAllTasks(limit);
    }

    @Override
    public List<Task> getTasks(int flowId) throws PersistenceException {
        return persistence.getTasks(flowId);
    }

    @Override
    public Task getTask(int taskId) throws PersistenceException {
        return persistence.getTask(taskId);
    }

    @Override
    public void cancelTask(Task task) throws PersistenceException {
        ExecutionRequest request = new ExecutionRequest(task.getId(), ExecutionRequest.Action.CANCEL_TASK);
        persistence.saveRequest(request);
    }

    @Override
    public void resumeTask(Task task) throws PersistenceException {
        ExecutionRequest request = new ExecutionRequest(task.getId(), ExecutionRequest.Action.RESUME_TASK);
        persistence.saveRequest(request);
    }

    @Override
    public Map<Integer,Unit> getAllUnits() throws PersistenceException {
        return persistence.getAllUnits();
    }

    @Override
    public void changeMachineActivityStatus(int machineId, boolean activate) throws PersistenceException {
        ExecutionRequest request = new ExecutionRequest(machineId,
                activate ? ExecutionRequest.Action.ACTIVATE_MACHINE : ExecutionRequest.Action.DEACTIVATE_MACHINE);
        persistence.saveRequest(request);
    }

    @Override
    public void generateBashFileForUnit(int unitId) throws PersistenceException {
        ExecutionRequest request = new ExecutionRequest(unitId, ExecutionRequest.Action.GENERATE_BASH);
        persistence.saveRequest(request);
    }

    @Override
    public int countRequests(Timestamp t) throws PersistenceException {
        return persistence.countRequests(t);
    }

    @Override
    public List<ExecutionRequest> getAllRequests(int limit) throws PersistenceException {
        return persistence.getAllRequests(limit);
    }

    public void clearDebugData() {
        try {
            ((PersistenceImpl) persistence).clearDebugData();
        } catch (PersistenceException e) {
            // do nothing - it is only relevant for tests and the exception is already logged
        }
    }
}

package ubongo.rest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.datatypes.*;
import ubongo.common.datatypes.unit.Unit;
import ubongo.common.datatypes.unit.UnitAdder;
import ubongo.persistence.Configuration;
import ubongo.persistence.Persistence;
import ubongo.persistence.PersistenceException;
import ubongo.persistence.PersistenceImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ServiceProviderImpl implements ServiceProvider {

    // TODO need to add ServletContextListener that will close connections on shutdown?

    private static Logger logger = LogManager.getLogger(ServiceProviderImpl.class);

    private Persistence persistence;
    private String unitsDirPath;

    public ServiceProviderImpl(Configuration configuration, String unitSettingsDirPath, boolean debug) {
        this.unitsDirPath = unitSettingsDirPath;
        persistence = new PersistenceImpl(unitSettingsDirPath,
                configuration.getDbConnectionProperties(), configuration.getSshConnectionProperties(),
                configuration.getMachines(), debug);
    }

    @Override
    public void start() {
        try {
            persistence.start();
        } catch (PersistenceException e) {
            System.out.println("Failed to start system persistence. Details:\n" + e.getMessage());
        }
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
        return persistence.getAllMachines();
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
    public List<String> showTaskLogs(int taskId) {
        return null; // TODO implement showTaskLogs
    }

    @Override
    public List<String> showServerLog() {
        return null; // TODO implement showServerLog
    }

    @Override
    public List<Unit> getAllUnits() throws PersistenceException {
        return persistence.getAllUnits();
    }

    @Override
    public void generateBashFileForNewUnit(int unitId) throws PersistenceException {
        List<Unit> allUnits = getAllUnits();
        if (allUnits.size() < unitId) {
            throw new PersistenceException("Configuration file was not found for unit " + unitId);
        }
        Unit unit = allUnits.get(unitId - 1);
        String unitBashPath = Paths.get(unitsDirPath, Unit.getUnitBashFileName(unit.getId())).toString();
        try {
            UnitAdder.generateBashFile(unit, unitBashPath);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(Paths.get(unitBashPath));
            } catch (IOException e1) {
                // ignore
            }
            throw new PersistenceException(e.getMessage(), e);
        }
    }

    public void clearDebugData() {
        try {
            ((PersistenceImpl) persistence).clearDebugData();
        } catch (PersistenceException e) {
            // do nothing - it is only relevant for tests and the exception is already logged
        }
    }
}

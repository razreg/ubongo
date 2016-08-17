package ubongo.persistence;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.datatypes.*;
import ubongo.common.datatypes.unit.Unit;
import ubongo.common.network.SSHConnectionProperties;
import ubongo.persistence.db.DBConnectionProperties;
import ubongo.persistence.db.DBProxy;
import ubongo.persistence.db.SQLExceptionHandler;
import ubongo.persistence.exceptions.DBProxyException;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

public class PersistenceImpl implements Persistence {

    /**
     * In some cases, failing queries or updates to the DB may produce successful results
     * given another chance (i.e. SQLTransientException).
     * MAX_NUM_RETRIES defines the number of retries in case of such errors.
     */
    private static final int MAX_NUM_RETRIES = 3;
    private static final Logger logger = LogManager.getLogger(PersistenceImpl.class);

    private DBProxy dbProxy;
    private SQLExceptionHandler sqlExceptionHandler;
    private UnitFetcher unitFetcher;

    public PersistenceImpl(String unitSettingsDirPath, DBConnectionProperties dbConnectionProperties,
                           List<Machine> machines, String queriesPath, boolean debug) {
        this(unitSettingsDirPath, dbConnectionProperties, null, machines, queriesPath, debug);
    }

    public PersistenceImpl(String unitSettingsDirPath, DBConnectionProperties dbConnectionProperties,
                           SSHConnectionProperties sshConnectionProperties, List<Machine> machines,
                           String queriesPath, boolean debug) {
        unitFetcher = new UnitFetcher(unitSettingsDirPath);
        dbProxy = sshConnectionProperties != null ?
                new DBProxy(unitFetcher, dbConnectionProperties, sshConnectionProperties, machines, queriesPath, debug) :
                new DBProxy(unitFetcher, dbConnectionProperties, machines, queriesPath, debug);
        sqlExceptionHandler = new SQLExceptionHandler(dbProxy);
    }

    @Override
    public void start() throws PersistenceException {
        new DBMethodInvoker<>(sqlExceptionHandler, dbProxy::start).invoke();
    }

    @Override
    public void stop() throws PersistenceException {
        dbProxy.disconnect();
    }

    @Override
    public void createAnalysis(String analysisName, List<Unit> units) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                dbProxy.createAnalysis(analysisName, units);
                return;
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public List<String> getAnalysisNames(int limit) throws PersistenceException {
        // TODO use limit param!
        return new DBMethodInvoker<>(sqlExceptionHandler, dbProxy::getAnalysisNames).invoke();
    }

    @Override
    public List<Unit> getAnalysis(String analysisName) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                return dbProxy.getAnalysis(analysisName);
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public int createFlow(String studyName, List<Task> tasks) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                return dbProxy.createFlow(studyName, tasks);
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public void startFlow(int flowId) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                dbProxy.startFlow(flowId);
                return;
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public List<Task> cancelFlow(int flowId) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                return dbProxy.cancelFlow(flowId);
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public List<Task> getNewTasks() throws PersistenceException {
        return new DBMethodInvoker<>(sqlExceptionHandler, dbProxy::getNewTasks).invoke();
    }

    @Override
    public void updateTaskStatus(Task task) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                dbProxy.updateStatus(task);
                return;
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public void updateTasksStatus(Collection<Task> waitingTasks) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                dbProxy.updateStatus(waitingTasks);
                return;
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public Task getTask(int taskId) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                return dbProxy.getTask(taskId);
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public List<Task> getTasks(int flowId) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                return dbProxy.getTasks(flowId);
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public boolean cancelTask(Task task) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                return dbProxy.cancelTask(task);
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public Unit getUnit(int unitId) throws PersistenceException {
        return unitFetcher.getUnit(unitId);
    }

    @Override
    public List<Unit> getAllUnits() throws PersistenceException {
        return new DBMethodInvoker<>(sqlExceptionHandler, unitFetcher::getAllUnits).invoke();
    }

    @Override
    public List<Task> getAllTasks(int limit) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                return dbProxy.getAllTasks(limit);
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public List<FlowData> getAllFlows(int limit) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                return dbProxy.getAllFlows(limit);
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public void resumeTask(int taskId) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                dbProxy.resumeTask(taskId);
                return;
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public List<ExecutionRequest> getNewRequests() throws PersistenceException {
        return new DBMethodInvoker<>(sqlExceptionHandler, dbProxy::getNewRequests).invoke();
    }

    @Override
    public void updateRequestStatus(ExecutionRequest request) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                dbProxy.updateRequestStatus(request);
                return;
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public void saveRequest(ExecutionRequest request) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                dbProxy.saveRequest(request);
                return;
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public void saveMachines(List<Machine> machines) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                dbProxy.saveMachines(machines);
                return;
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public List<Machine> getAllMachines() throws PersistenceException {
        return new DBMethodInvoker<>(sqlExceptionHandler, dbProxy::getAllMachines).invoke();
    }

    @Override
    public void updateMachine(Machine machine) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                dbProxy.updateMachine(machine);
                return;
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public void changeMachineActivityStatus(int machineId, boolean activate) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                dbProxy.changeMachineActivityStatus(machineId, activate);
                return;
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    public void clearDebugData() throws PersistenceException {
        new DBMethodInvoker<>(sqlExceptionHandler, dbProxy::clearAllDebugTables).invoke();
    }

    private DBProxyException handleDbProxyException(DBProxyException e, int numRetries) {
        Throwable t = e.getCause();
        if (numRetries == MAX_NUM_RETRIES || !(t instanceof SQLException)
                || !sqlExceptionHandler.isRecoverable((SQLException) t)) {
            logger.error(e.getMessage(), e);
            return e;
        }
        return null;
    }

    /**
     * This class is used to manage execution and error handling of different persistence methods. It enables invoking
     * function calls with an exception handler that catches the exception thrown by the function, checks if it is
     * recoverable, and if so, tries to recover from it (by retrying and/or restarting DB connection). It only
     * encapsulates calls to methods with no arguments.
     * @param <T> is the return value of the invoked method.
     */
    private class DBMethodInvoker<T> {

        private Callable<T> callable;
        private SQLExceptionHandler sqlExceptionHandler;

        public DBMethodInvoker(SQLExceptionHandler sqlExceptionHandler, Callable<T> func) {
            this.callable = func;
            this.sqlExceptionHandler = sqlExceptionHandler;
        }

        public T invoke() throws DBProxyException {
            int numRetries = 0;
            String errMsg = "Failed to invoke method";
            DBProxyException dbProxyException = new DBProxyException(errMsg);
            while (numRetries++ < MAX_NUM_RETRIES) {
                try {
                    return callable.call();
                } catch (DBProxyException e) {
                    dbProxyException = e;
                    Throwable t = e.getCause();
                    if (!(t instanceof SQLException) || !sqlExceptionHandler.isRecoverable((SQLException) t)) {
                        throw e;
                    }
                } catch (Exception e) {
                    throw new DBProxyException(errMsg, e);
                }
            }
            throw dbProxyException;
        }
    }

}

package ubongo.persistence;

import ubongo.common.datatypes.*;
import ubongo.common.datatypes.unit.Unit;
import ubongo.common.network.SSHConnectionProperties;
import ubongo.persistence.db.DBConnectionProperties;
import ubongo.persistence.db.DBProxy;
import ubongo.persistence.db.SQLExceptionHandler;
import ubongo.persistence.exceptions.DBProxyException;
import ubongo.persistence.exceptions.PersistenceException;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * This class implements the Persistence API. All methods in this implementations utilize a mechanism to try and handle
 * runtime problems instead of immediately failing. In some cases, writing to a Database or reading from it may cause
 * transient exceptions that can be easily solved with retrying or restarting the connection. To do this, we use two
 * schemes: in case there are no arguments to the method, we use {@link DBMethodInvoker} to invoke the method responsibly;
 * and if the method does have arguments, we run its content within a loop for retries, if they might help.
 */
public class PersistenceImpl implements Persistence {

    /**
     * In some cases, failing queries or updates to the DB may produce successful results
     * given another chance (i.e. SQLTransientException).
     * MAX_NUM_RETRIES defines the number of retries in case of such errors.
     */
    private static final int MAX_NUM_RETRIES = 3;

    private DBProxy dbProxy;
    private SQLExceptionHandler sqlExceptionHandler;
    private UnitFetcher unitFetcher;

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
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                return dbProxy.getAnalysisNames(limit);
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
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
    public int createFlow(Context context, List<Task> tasks) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                return dbProxy.createFlow(context, tasks);
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
    public Map<Integer,Unit> getAllUnits() throws PersistenceException {
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
    public List<ExecutionRequest> getAllRequests(int limit) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                return dbProxy.getAllRequests(limit);
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public int countRequests(Timestamp t) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                return dbProxy.countRequests(t);
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
    public List<Machine> getAllMachines(boolean includeServer) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                return dbProxy.getAllMachines(includeServer);
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
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

    @Override
    public void insertContextToTask(Task originalTask, List<Task> replacements) throws PersistenceException {
        int numRetries = 0;
        while (numRetries++ < MAX_NUM_RETRIES) {
            try {
                dbProxy.insertContextToTask(originalTask, replacements);
                return;
            } catch (DBProxyException e) {
                DBProxyException ret;
                if ((ret = handleDbProxyException(e, numRetries)) != null) throw ret;
            }
        }
        throw new PersistenceException("Unknown reason"); // not possible
    }

    @Override
    public void performCleanup() throws PersistenceException {
        new DBMethodInvoker<>(sqlExceptionHandler, dbProxy::performCleanup).invoke();
    }

    @Override
    public List<Task> getProcessingTasks() throws PersistenceException {
        return new DBMethodInvoker<>(sqlExceptionHandler, dbProxy::getProcessingTasks).invoke();
    }

    public void clearDebugData() throws PersistenceException {
        new DBMethodInvoker<>(sqlExceptionHandler, dbProxy::clearAllDebugTables).invoke();
    }

    private DBProxyException handleDbProxyException(DBProxyException e, int numRetries) {
        Throwable t = e.getCause();
        if (numRetries == MAX_NUM_RETRIES || !(t instanceof SQLException)
                || !sqlExceptionHandler.isRecoverable((SQLException) t)) {
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

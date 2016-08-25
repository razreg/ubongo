package ubongo.server;

import com.rabbitmq.client.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.constants.SystemConstants;
import ubongo.common.datatypes.*;
import ubongo.common.datatypes.unit.Unit;
import ubongo.persistence.Configuration;
import ubongo.persistence.Persistence;
import ubongo.persistence.UnitAdder;
import ubongo.persistence.exceptions.PersistenceException;
import ubongo.persistence.PersistenceImpl;
import ubongo.server.exceptions.MachinesManagementException;

import javax.xml.bind.UnmarshalException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class expects the following JVM options (i.e., variables passed as "java -Dvar_name=value -jar jar_name"):
 *  -Dconfig - path to the ubongo-config.xml file (e.g., /some/path/config/ubongo-config.xml)
 *  -Dunits_path - path to the units directory (e.g., /some/path/units)
 *  -Dqueries - path to the queries.properties file (e.g., /some/path/db/queries.properties)
 *  -Dlog_directory - path to log directory (e.g., /some/path/to/log/dir) - used by log4j configuration
 */
public class ExecutionServer {

    private static final String CONFIG_PATH = "config";
    private static final String UNITS_DIR_PATH = "units_path";
    private static final String QUERIES_PATH = "queries";

    private static volatile boolean keepRunning = true;
    private static final int SECONDS_INTERVAL_FOR_REQUESTS_HANDLER = 20;

    private static Logger logger = LogManager.getLogger(ExecutionServer.class);
    private static ScheduledExecutorService requestsHandler;
    private static MachinesManager machinesManager;
    private static QueueManager queueManager;
    private static ExecutionProxy executionProxy;
    private static Persistence persistence;
    private static String unitsDirPath;

    public static void main(String[] args) {
        try {
            executionServerMain();
        } catch (Exception e) {
            logger.fatal("Server shutting down due to an unexpected exception", e);
            stop();
        }
    }

    private static void executionServerMain() {
        logger.info("Starting the execution server...");
        keepRunning = true;
        String configPath = System.getProperty(CONFIG_PATH);
        unitsDirPath = System.getProperty(UNITS_DIR_PATH);
        String queriesPath = System.getProperty(QUERIES_PATH);
        if (!validSystemVariables(configPath, unitsDirPath, queriesPath)) return;
        try {
            initServer(configPath, unitsDirPath, queriesPath);
        } catch (UnmarshalException e) {
            logger.fatal("Failed to init server", e);
            return;
        }
        runServer();
    }

    private static boolean validSystemVariables(String configPath, String unitsDirPath, String queriesPath) {
        String pattern = "Please supply %1$s path as run parameter: -D%2$s=<path>";
        boolean invalid;
        if (invalid = configPath == null) {
            System.out.format(pattern, "configuration", CONFIG_PATH);
        }
        if (unitsDirPath == null) {
            System.out.format(pattern, "units directory", UNITS_DIR_PATH);
            invalid = true;
        }
        if (queriesPath == null) {
            System.out.format(pattern, "queries.properties", UNITS_DIR_PATH);
            invalid = true;
        }
        return !invalid;
    }

    private static void initServer(String configPath, String unitsDirPath, String queriesPath) throws UnmarshalException {
        Configuration configuration = Configuration.loadConfiguration(configPath);
        List<Machine> machines = configuration.getMachines();
        persistence = new PersistenceImpl(unitsDirPath, configuration.getDbConnectionProperties(),
                configuration.getSshConnectionProperties(), machines, queriesPath, configuration.getDebug());
        executionProxy = ExecutionProxy.getInstance();
        machinesManager = new MachinesManager(machines, persistence);
        queueManager = new QueueManager(persistence, machinesManager);
        requestsHandler = Executors.newScheduledThreadPool(1);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private static void runServer() {
        final Thread mainThread = Thread.currentThread();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                keepRunning = false;
                try {
                    mainThread.join();
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        });
        start();
        while(keepRunning);
        stop();
    }

    private static void start() {
        try {
            persistence.start();
        } catch (PersistenceException e) {
            queueManager = null;
            machinesManager = null;
            logger.fatal("Server has failed to start the persistence module.", e);
            keepRunning = false;
            return;
        }
        try {
            machinesManager.start();
        } catch (PersistenceException e) {
            queueManager = null;
            logger.fatal("Server has failed to start the machines manager.", e);
            keepRunning = false;
            return;
        }
        try {
            cleanup(); // also starts the queue manager!
        } catch (PersistenceException e) {
            logger.fatal("Server has failed to perform cleanup on the database.", e);
            keepRunning = false;
            return;
        }
        try {
            tasksStatusListener();
        } catch (Exception e) {
            queueManager = null;
            logger.fatal("Server has failed to start the task status listener (RabbitMQ listener).", e);
            keepRunning = false;
            return;
        }

        requestsHandler.scheduleAtFixedRate(() -> {
            List<ExecutionRequest> requests;
            try {
                requests = persistence.getNewRequests();
            } catch (PersistenceException e) {
                notifyFatal(e);
                return;
            }
            requests.stream().forEach(ExecutionServer::handleRequest);
        }, 0, SECONDS_INTERVAL_FOR_REQUESTS_HANDLER, TimeUnit.SECONDS);
    }

    private static void stop() {
        logger.info("Server is shutting down...");
        if (queueManager != null) queueManager.stop();
        if (machinesManager != null) machinesManager.stop();
        if (persistence != null) {
            try {
                persistence.stop();
            } catch (PersistenceException e) {
                logger.warn("Server has failed to stop the persistence module.", e);
            }
        }
        if (requestsHandler != null && !requestsHandler.isTerminated()) {
            requestsHandler.shutdown();
            boolean terminated;
            try {
                terminated =
                        requestsHandler.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                terminated = false;
            }
            if (!terminated) {
                logger.warn("Server has failed to stop the requests handler.");
            }
        }
    }

    private static void handleRequest(ExecutionRequest request) {
        ExecutionRequest.Status newStatus = ExecutionRequest.Status.COMPLETED;
        int entityId = request.getEntityId();
        try {
            switch (request.getAction()) {
                case CANCEL_TASK:
                    cancelTask(persistence.getTask(entityId));
                    break;
                case KILL_TASK:
                    killTask(persistence.getTask(entityId));
                    break;
                case RESUME_TASK:
                    queueManager.resumeTask(entityId);
                    break;
                case RUN_FLOW:
                    queueManager.startFlow(entityId);
                    break;
                case CANCEL_FLOW:
                    cancelFlow(entityId);
                    break;
                case ACTIVATE_MACHINE:
                    changeMachineActivityStatus(entityId, true);
                    break;
                case DEACTIVATE_MACHINE:
                    changeMachineActivityStatus(entityId, false);
                    break;
                case GENERATE_BASH:
                    generateBashFileForUnit(entityId);
                    break;
            }
        } catch (Exception e) {
            logger.error("Server has failed to handle request (id="
                    + request.getId() + ", type="
                    + request.getAction() + ")", e);
            newStatus = ExecutionRequest.Status.FAILED;
        } finally {
            request.setStatus(newStatus);
            try {
                persistence.updateRequestStatus(request);
            } catch (PersistenceException e) {
                logger.error("Server has failed to update status of request (id="
                        + request.getId() + ", type="
                        + request.getAction() + ") to status="
                        + request.getStatus(), e);
            }
        }
    }

    private static void changeMachineActivityStatus(int machineId, boolean activate) throws PersistenceException {
        persistence.changeMachineActivityStatus(machineId, activate);
        machinesManager.setMachines(persistence.getAllMachines(false));
    }

    private static void killTask(Task task) {
        if (task.getMachine() == null) {
            logger.warn("Tried to stop task with null machine");
        } else {
            executionProxy.killTask(task);
            boolean setFailed;
            try {
                setFailed = !machinesManager.isAvailable(task.getMachine().getId());
            } catch (MachinesManagementException e) {
                setFailed = true;
            }
            if (setFailed) {
                task.setStatus(TaskStatus.FAILED);
                queueManager.updateTaskAfterExecution(task);
                if (logger.isInfoEnabled()) {
                    logger.info("A request to stop task with id=" + task.getId() + " was sent to machine with id=" + task.getMachine().getId()
                            + ". However, this machine is not available and therefore the task's status was changed to 'Failed'");
                }
            } else if (logger.isInfoEnabled()) {
                logger.info("A request to stop task with id=" + task.getId()
                        + " was sent to machine with id=" + task.getMachine().getId());
            }
        }
    }

    private static void cancelTask(Task task) throws PersistenceException {
        try {
            ExecutionServer.notifyQueueBeforeCancel(task);
            if (!persistence.cancelTask(task)) {
                killTask(task); // task could not be canceled - need to be killed
            }
        }
        finally {
            ExecutionServer.notifyQueueAfterCancel(task);
        }
    }

    private static void cancelFlow(int flowId) throws PersistenceException {
        List<Task> flowTasks = persistence.getTasks(flowId);
        try {
            flowTasks.forEach(ExecutionServer::notifyQueueBeforeCancel);
            List<Task> tasksToKill = persistence.cancelFlow(flowId);
            tasksToKill.forEach(ExecutionServer::killTask);
        }
        finally {
            flowTasks.forEach(ExecutionServer::notifyQueueAfterCancel);
        }
    }

    /**
     * A call to this function notifies the Server that a fatal error has occurred and then it tries to take some
     * actions to repair the bad situation that might have been caused.
     * @param e throwable that caused the fatal error.
     */
    protected static void notifyFatal(Throwable e) {
        /* currently we do not care what Throwable resulted in this fatal because
           we always just perform a cleanup routine */
        logger.info("Fatal error occurred. Restarting server...");
        stop();
        executionServerMain();
    }

    private static void generateBashFileForUnit(int unitId) throws PersistenceException {
        Map<Integer,Unit> allUnits = persistence.getAllUnits();
        Unit unit = allUnits.get(unitId);
        if (unit == null) {
            throw new PersistenceException("Configuration file was not found for unit with id=" + unitId);
        }
        String unitBashPath = Paths.get(unitsDirPath, Unit.getUnitBashFileName(unit.getId())).toString();
        try {
            UnitAdder.generateBashFile(unit, unitBashPath);
        } catch (Exception e) {
            try {
                Files.deleteIfExists(Paths.get(unitBashPath));
            } catch (IOException e1) {
                // ignore
            }
            throw new PersistenceException("Failed to generate bash file for unit with id=" + unitId, e);
        }
    }

    private static void cleanup() throws PersistenceException {
        logger.info("Performing database cleanup");
        persistence.performCleanup();
        List<Task> processing = persistence.getProcessingTasks();
        queueManager.start();
        processing.forEach(ExecutionServer::killTask);
    }

    private static void tasksStatusListener() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        String queue = SystemConstants.UBONGO_SERVER_TASKS_STATUS_QUEUE;
        channel.queueDeclare(queue, false, false, false, null);
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
                    throws IOException {
                try {
                    RabbitData message = RabbitData.fromBytes(body);
                    logger.info("Received message '" + message.getMessage() + "' from RabbitMQ");
                    Task task = message.getTask();
                    queueManager.updateTaskAfterExecution(task);
                } catch (Exception e){
                    throw new IOException(e);
                }
            }
        };
        channel.basicConsume(queue, true, consumer);
    }

    private static void notifyQueueBeforeCancel(Task task) {
        queueManager.aboutToCancel(task);
    }

    private static void notifyQueueAfterCancel(Task task) {
        queueManager.cancelCompleted(task);
    }
}

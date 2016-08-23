package ubongo.server;

import com.rabbitmq.client.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.constants.SystemConstants;
import ubongo.common.datatypes.ExecutionRequest;
import ubongo.common.datatypes.Machine;
import ubongo.common.datatypes.RabbitData;
import ubongo.common.datatypes.Task;
import ubongo.persistence.Configuration;
import ubongo.persistence.Persistence;
import ubongo.persistence.exceptions.PersistenceException;
import ubongo.persistence.PersistenceImpl;

import javax.xml.bind.UnmarshalException;
import java.io.IOException;
import java.util.List;
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

    private static ExecutionServer INSTANCE;

    private static final String CONFIG_PATH = "config";
    private static final String UNITS_DIR_PATH = "units_path";
    private static final String QUERIES_PATH = "queries";

    private static volatile boolean keepRunning = true;
    private static final int SECONDS_INTERVAL_FOR_REQUESTS_HANDLER = 20;

    private static Logger logger = LogManager.getLogger(ExecutionServer.class);
    private final ScheduledExecutorService requestsHandler = Executors.newScheduledThreadPool(1);
    private MachinesManager machinesManager;
    private QueueManager queueManager;
    private ExecutionProxy executionProxy;
    private Persistence persistence;

    public static void main(String[] args) {
        String configPath = System.getProperty(CONFIG_PATH);
        String unitsDirPath = System.getProperty(UNITS_DIR_PATH);
        String queriesPath = System.getProperty(QUERIES_PATH);
        if (!validSystemVariables(configPath, unitsDirPath, queriesPath)) return;
        try {
            INSTANCE = initServer(configPath, unitsDirPath, queriesPath);
        } catch (UnmarshalException e) {
            logger.error("Failed to init server.", e);
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

    private static ExecutionServer initServer(String configPath, String unitsDirPath, String queriesPath) throws UnmarshalException {
        Configuration configuration = Configuration.loadConfiguration(configPath);
        List<Machine> machines = configuration.getMachines();
        Persistence persistence = new PersistenceImpl(unitsDirPath, configuration.getDbConnectionProperties(),
                configuration.getSshConnectionProperties(), machines, queriesPath, configuration.getDebug());
        return new ExecutionServer(persistence, machines);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private static void runServer() {
        INSTANCE.start();
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
        while(keepRunning);
        INSTANCE.stop();
    }

    private ExecutionServer(Persistence persistence, List<Machine> machines) {
        this.persistence = persistence;
        executionProxy = ExecutionProxy.getInstance();
        machinesManager = new MachinesManager(machines, persistence);
        queueManager = new QueueManager(persistence, machinesManager);
    }

    private void start() {
        try {
            persistence.start();
        } catch (PersistenceException e) {
            queueManager = null;
            machinesManager = null;
            logger.fatal("Server failed to start the persistence module.");
            keepRunning = false;
            return;
        }
        try {
            machinesManager.start();
        } catch (PersistenceException e) {
            queueManager = null;
            logger.fatal("Server failed to start the machines manager.");
            keepRunning = false;
            return;
        }
        queueManager.start();
        try {
            tasksStatusListener();
        } catch (Exception e) {
            queueManager = null;
            logger.fatal("Server failed to start the task status listener (RabbitMQ listener).");
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

    private void stop() {
        if (queueManager != null) queueManager.stop();
        if (machinesManager != null) machinesManager.stop();
        if (persistence != null) {
            try {
                persistence.stop();
            } catch (PersistenceException e) {
                logger.error("Server failed to stop the persistence module.", e);
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
                logger.error("Server failed to stop the requests handler.");
            }
        }
    }

    private static void handleRequest(ExecutionRequest request) {
        ExecutionRequest.Status newStatus = ExecutionRequest.Status.COMPLETED;
        int entityId = request.getEntityId();
        try {
            switch (request.getAction()) {
                case CANCEL_TASK:
                    cancelTask(INSTANCE.persistence.getTask(entityId));
                    break;
                case KILL_TASK:
                    killTask(INSTANCE.persistence.getTask(entityId));
                    break;
                case RESUME_TASK:
                    INSTANCE.queueManager.resumeTask(entityId);
                    break;
                case RUN_FLOW:
                    INSTANCE.queueManager.startFlow(entityId);
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
            }
        } catch (Exception e) {
            logger.error("Server failed to handle request (id="
                    + request.getId() + ", type="
                    + request.getAction() + ")", e);
            newStatus = ExecutionRequest.Status.FAILED;
        } finally {
            request.setStatus(newStatus);
            try {
                INSTANCE.persistence.updateRequestStatus(request);
            } catch (PersistenceException e) {
                logger.error("Server failed to update status of request (id="
                        + request.getId() + ", type="
                        + request.getAction() + ") to status="
                        + request.getStatus(), e);
            }
        }
    }

    private static void changeMachineActivityStatus(int machineId, boolean activate) throws PersistenceException {
        INSTANCE.persistence.changeMachineActivityStatus(machineId, activate);
        INSTANCE.machinesManager.setMachines(INSTANCE.persistence.getAllMachines(false));
    }

    private static void killTask(Task task) {
        INSTANCE.executionProxy.killTask(task);
    }

    private static void cancelTask(Task task) throws PersistenceException {
        try {
            ExecutionServer.notifyQueueBeforeCancel(task);
            if (!INSTANCE.persistence.cancelTask(task)) {
                killTask(task); // task could not be canceled - need to be killed
            }
        }
        finally {
            ExecutionServer.notifyQueueAfterCancel(task);
        }
    }

    private static void cancelFlow(int flowId) throws PersistenceException {
        List<Task> flowTasks = INSTANCE.persistence.getTasks(flowId);
        try {
            flowTasks.forEach(ExecutionServer::notifyQueueBeforeCancel);
            List<Task> tasksToKill = INSTANCE.persistence.cancelFlow(flowId);
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

    }

    private void tasksStatusListener() throws IOException, TimeoutException {
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
                    System.out.println(" [!] Received '" + message.getMessage() + "'");
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
        INSTANCE.queueManager.aboutToCancel(task);
    }

    private static void notifyQueueAfterCancel(Task task) {
        INSTANCE.queueManager.cancelCompleted(task);
    }
}

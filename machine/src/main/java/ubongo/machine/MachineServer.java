package ubongo.machine;

import com.rabbitmq.client.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.constants.MachineConstants;
import ubongo.common.constants.SystemConstants;
import ubongo.common.datatypes.RabbitData;
import ubongo.common.datatypes.Task;
import ubongo.persistence.*;

import javax.xml.bind.UnmarshalException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MachineServer run on each machine all the time, and listens to socket requests.
 * When a request arrives - the MachineServer creates the required objects and call the MachineControllerImpl.
 */
public class MachineServer {

    private static Logger logger = LogManager.getLogger(MachineServer.class);

    public static Map<String, Thread> unitThreads;
    private static Configuration configuration;
    private static String serverAddress;
    private static String unitsDir;
    private static String queriesPath;
    private static ScheduledExecutorService heartbeatScheduler = Executors.newScheduledThreadPool(1);

    public MachineServer() {
        unitThreads = new HashMap<>();
        serverAddress = MachineConstants.SERVER_FALLBACK;
    }

    public void start() throws PersistenceException {
        Persistence persistence = new PersistenceImpl(unitsDir, configuration.getDbConnectionProperties(),
                configuration.getSshConnectionProperties(), null, queriesPath, true); // TODO change last argument to some system property (debug)
        persistence.start();
        initHeartbeat(persistence);
    }

    public static void main(String[] args) {

        logger.info("Initializing machine-server...");
        if (!initSystemPropertyObjects()) return;
        MachineServer machineServer = new MachineServer();
        try {
            machineServer.start();
        } catch (PersistenceException e) {
            logger.error("Failed to start persistence module in the machine", e);
        }
        final String TASKS_QUEUE_NAME = SystemConstants.UBONGO_RABBIT_TASKS_QUEUE;
        final String KILL_TASKS_QUEUE_NAME = SystemConstants.UBONGO_RABBIT_KILL_TASKS_QUEUE;
        try {
            logger.info("[!] Waiting for new tasks.");
            tasksListener(TASKS_QUEUE_NAME, '+');
            tasksListener(KILL_TASKS_QUEUE_NAME, 'x');
        } catch (Exception e){
            logger.error("Failed receiving message via rabbit mq error: " + e.getMessage(), e);
        }
    }

    private static boolean initSystemPropertyObjects() {
        unitsDir = System.getProperty(MachineConstants.ARG_UNITS);
        String configPath = System.getProperty(MachineConstants.ARG_CONFIG_PATH);
        queriesPath = System.getProperty(MachineConstants.ARG_QUERIES_PATH);
        boolean ret = true;
        if (unitsDir == null) {
            logger.error("Please supply units directory path as run parameter: -D" +
                    MachineConstants.ARG_UNITS + "=<path>");
            ret = false;
        } else if (queriesPath == null) {
            logger.error("Please supply path to queries.properties as run parameter: -D" +
                    MachineConstants.ARG_QUERIES_PATH + "=<path>");
            ret = false;
        } else if (configPath == null) {
            logger.error("Please supply configuration path as run parameter: -D" +
                    MachineConstants.ARG_CONFIG_PATH + "=<path>");
            ret = false;
        }
        if (!ret) return false;
        try {
            configuration = Configuration.loadConfiguration(configPath);
        } catch (UnmarshalException e) {
            logger.error("Configuration path parameter is not a file or the configuration file is invalid");
            return false;
        }
        return true;
    }

    private static void tasksListener(String queue, char actionSign) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.queueDeclare(queue, false, false, false, null);
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
                    throws IOException {
                try {
                    RabbitData message = RabbitData.fromBytes(body);
                    logger.info(" ["+actionSign+"] Received '" + message.getMessage() + "'");
                    String baseDir = System.getProperty(MachineConstants.ARG_DIR);
                    String unitsDir = System.getProperty(MachineConstants.ARG_UNITS);
                    serverAddress = System.getProperty(MachineConstants.ARG_SERVER);
                    logger.info("Server address: [" + serverAddress + "], base directory path: [" + baseDir + "] , units directory path: [" + unitsDir + "]");
                    String threadName = getThreadName(message.getTask());
                    RequestHandler requestHandler =
                            new RequestHandler(threadName, message, serverAddress, baseDir, unitsDir, configuration);
                    if ((message.getMessage()).equals(MachineConstants.BASE_UNIT_REQUEST)) {
                        unitThreads.put(threadName, requestHandler);
                    }
                    logger.info("Starting RequestHandler thread");
                    requestHandler.start();
                } catch (Exception e){
                    logger.error("Failed receiving rabbitMq message. ", e);
                    throw new IOException(e);
                }
            }
        };
        channel.basicConsume(queue, true, consumer);
    }

    public static String getThreadName(Task task) {
        return MachineConstants.BASE_UNIT_REQUEST + task.getId();
    }

    private static void initHeartbeat(Persistence persistence) throws PersistenceException {
        InetAddress ip;
        try {
            ip = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw new PersistenceException("Failed to get machine's IP address", e);
        }
        String host = ip.getHostAddress();
        logger.info("Starting to send heartbeat for host: " + host);
        final Runnable heartbeatSender = new HeartbeatSender(persistence, host);
        heartbeatScheduler.scheduleAtFixedRate(heartbeatSender, 0, 60, TimeUnit.SECONDS);
    }

}
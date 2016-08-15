package ubongo.machine;

import com.rabbitmq.client.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.constants.MachineConstants;
import ubongo.common.constants.SystemConstants;
import ubongo.common.datatypes.MachineStatistics;
import ubongo.common.datatypes.RabbitData;
import ubongo.common.datatypes.Task;

import java.io.IOException;
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

    public static Map<String, Thread> unitThreads;

    private static Logger logger = LogManager.getLogger(MachineServer.class);

    private MachineStatistics machineStatistics;
    static String serverAddress;

    public MachineServer() {
        unitThreads = new HashMap<>();
        this.machineStatistics = new MachineStatistics(unitThreads.size());
        serverAddress = MachineConstants.SERVER_FALLBACK;
    }

    public void start() {
        trackMachinePerformance();
    }

    public static void main(String[] args) {

        logger.info("Initializing machine-server...");
        MachineServer machineServer = new MachineServer();
        // machineServer.start(); // TODO remove comment
        final String TASKS_QUEUE_NAME = SystemConstants.UBONGO_RABBIT_TASKS_QUEUE;
        final String KILL_TASKS_QUEUE_NAME = SystemConstants.UBONGO_RABBIT_KILL_TASKS_QUEUE;
        try {
            System.out.println(" [*] Waiting for new tasks. To exit press CTRL+C");
            tasksListener(TASKS_QUEUE_NAME, '+');
            tasksListener(KILL_TASKS_QUEUE_NAME, 'x');
        } catch (Exception e){
            logger.error("Failed receiving message via rabbit mq error: " + e.getMessage());
        }
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
                    String configPath = System.getProperty(MachineConstants.CONFIG_PATH);
                    logger.info("Server address: [" + serverAddress + "], base directory path: [" + baseDir + "]");
                    String threadName = getThreadName(message.getTask());
                    RequestHandler requestHandler = new RequestHandler(threadName, message, serverAddress, baseDir, unitsDir, configPath);
                    if ((message.getMessage()).equals(MachineConstants.BASE_UNIT_REQUEST)) {
                        unitThreads.put(threadName, requestHandler);
                    }
                    logger.debug("Starting RequestHandler thread...");
                    requestHandler.start();
                } catch (Exception e){
                    throw new IOException(e);
                }
            }
        };
        channel.basicConsume(queue, true, consumer);
    }

    public static String getThreadName(Task task) {
        return MachineConstants.BASE_UNIT_REQUEST + task.getId();
    }

    private void trackMachinePerformance() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        final Runnable sampler = new MachinePerformanceSampler(machineStatistics);
        scheduler.scheduleAtFixedRate(sampler, 0, 30, TimeUnit.SECONDS);

    }

}
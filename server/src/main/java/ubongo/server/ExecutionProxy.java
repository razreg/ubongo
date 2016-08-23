package ubongo.server;

import com.rabbitmq.client.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.constants.MachineConstants;
import ubongo.common.constants.SystemConstants;
import ubongo.common.datatypes.*;

public enum ExecutionProxy {

    INSTANCE; // This is a singleton

    public static ExecutionProxy getInstance() {
        return INSTANCE;
    }

    private static Logger logger = LogManager.getLogger(ExecutionProxy.class);
    private String inputDirPath;
    private QueueManager queueManager;

    /**
     * @param task to execute
     * @param queueManager to send the task back after execution
     */
    public void execute(Task task, QueueManager queueManager) {
        this.queueManager = queueManager;
        sendRequestToMachine(task, SystemConstants.UBONGO_RABBIT_TASKS_QUEUE,
                MachineConstants.BASE_UNIT_REQUEST );
    }

    public void killTask(Task task) {
        sendRequestToMachine(task, SystemConstants.UBONGO_RABBIT_KILL_TASKS_QUEUE,
                MachineConstants.KILL_TASK_REQUEST );
    }

    private void sendRequestToMachine(Task task, String queue, String request) {
        logger.info("Sending request to the machine. Queue = [" + queue+ "] RequestTask = [ "+ request+ "] id = [" + task.getId() + "]");
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(task.getMachine().getDescription());
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();
            channel.queueDeclare(queue, false, false, false, null);
            RabbitData message = new RabbitData(task, request);
            channel.basicPublish("", queue, null, message.getBytes());
            if (logger.isDebugEnabled()) {
                logger.debug(" [x] Sent '" + message.getMessage() + "'");
            }
            channel.close();
            connection.close();
        } catch (Exception e){
            logger.error("Failed sending task to machine. Task id = [" + task.getId() + "] Machine = [" + task.getMachine().getHost() + "] error: " + e.getMessage());
            task.setStatus(TaskStatus.FAILED);
            queueManager.updateTaskAfterExecution(task);
        }
    }

}
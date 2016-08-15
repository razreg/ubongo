package ubongo.machine;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.FileUtils;
import ubongo.common.constants.MachineConstants;
import ubongo.common.constants.SystemConstants;
import ubongo.common.datatypes.RabbitData;
import ubongo.common.datatypes.Task;
import ubongo.common.datatypes.TaskStatus;
import ubongo.common.exceptions.NetworkException;
import ubongo.common.network.SSHConnectionProperties;
import ubongo.common.network.SftpManager;
import ubongo.persistence.Configuration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * RequestHandler is called by the MachineServer when a new request arrives.
 * When a request arrive - the RequestHandler create the required objects and call the MachineControllerImpl.
 */

public class RequestHandler extends Thread {

    private static Logger logger = LogManager.getLogger(RequestHandler.class);
    private String baseDir; // The root directory where the files should be stored
    private String unitsDir; // The directory where the units should be stored, related to the base dir
    private String serverAddress; // Address of the program server
    private String configPath; // The directory where the configuration files should be stored
    private RabbitData rabbitMessage;
    private SSHConnectionProperties sshConnectionProperties;

    private String tmpInputFilesDir = "";
    private String tmpOutputFilesDir = "";

    private Task task;

    public RequestHandler(String threadName, RabbitData rabbitMessage, String serverAddress, String baseDir, String unitsDir, String configPath) {
        super(threadName);
        this.baseDir = baseDir;
        this.unitsDir = unitsDir;
        this.serverAddress = serverAddress;
        this.rabbitMessage = rabbitMessage;
        this.configPath = configPath;
        if (logger.isDebugEnabled()) {
            logger.debug("serverAddress = [" + serverAddress + "] baseDir = [" + baseDir + "] configPath = [" + configPath + "] " +
                    "unitsDir = [" + unitsDir + "] message = [" + rabbitMessage.getMessage() + "]");
        }
    }

    @Override
    public void run() {
        try {
            Configuration configuration = Configuration.loadConfiguration(configPath);
            String machineWorkDir = configuration.getUnitsMainProperties().getMachineWorkspaceDir();
            sshConnectionProperties = configuration.getSshConnectionProperties();
            this.task = rabbitMessage.getTask();
            logger.info("[Study = " + task.getContext().getStudy() + "] Parsed request = [" + rabbitMessage.getMessage() + " " + task.getId() + "]");
            if (rabbitMessage.getMessage().equals(MachineConstants.BASE_UNIT_REQUEST)) {
                this.tmpOutputFilesDir = this.baseDir + File.separator + machineWorkDir + File.separator + task.getId() + MachineConstants.OUTPUT_DIR_SUFFIX;
                this.tmpInputFilesDir = this.baseDir + File.separator + machineWorkDir + File.separator + task.getId() + MachineConstants.INPUT_DIR_SUFFIX;
                handleBaseUnitRequest(machineWorkDir);
                removeThreadFromCollection();
            } else if (rabbitMessage.getMessage().equals(MachineConstants.KILL_TASK_REQUEST)) {
                handleKillRequest();
            } else if (rabbitMessage.getMessage().equals(MachineConstants.GET_MACHINE_PERFORMANCE)) {
                handlePerformanceRequest();
            }
        } catch (InterruptedException ie) {
            try {
                logger.debug("[Study = " + task.getContext().getStudy() + "] InterruptedException - calling handleStopInterrupt");
                handleStopInterrupt(true);
            } catch (HandledInterruptedException e) {
                logger.debug("[Study = " + task.getContext().getStudy() + "] HandledInterruptedException");
                // do nothing :)
            }
        } catch (HandledInterruptedException hie) {
            // do nothing :)
        } catch (Exception e) {
            logger.error("[Study = " + task.getContext().getStudy() + "] Failed handling request: " + e.getMessage(), e);
        } catch (Throwable throwable) {
            if (throwable instanceof InterruptedException) {
                logger.debug("(throwable instanceof InterruptedException");
                try {
                    handleStopInterrupt(true);
                } catch (HandledInterruptedException e) {
                    // do nothing :)
                }
            } else {
                logger.error("[Study = " + task.getContext().getStudy() + "] Failed handling request: " + throwable.getMessage(), throwable);
            }
        }
    }

    private void removeThreadFromCollection() {
        String threadName = MachineServer.getThreadName(task);
        if (MachineServer.unitThreads.containsKey(threadName))
            MachineServer.unitThreads.remove(threadName);
    }

    private void handlePerformanceRequest() { // TODO !!!!
    }

    private void handleKillRequest() {
        String threadName = MachineServer.getThreadName(task);
        if (MachineServer.unitThreads.containsKey(threadName)){
            Thread currTaskThread = MachineServer.unitThreads.get(threadName);
            currTaskThread.interrupt();
            logger.info("[Study = " + task.getContext().getStudy() + "] Thread [" + currTaskThread.getName() + "] " +
                    " with id = [" +currTaskThread.getId() + "] was interrupted.");
        } else {
            logger.info("[Study = " + task.getContext().getStudy() + "] Thread " + threadName + " is not running on the machine.");
        }
    }

    private boolean handleReceiveFiles(String filesSourceDir) throws Throwable {
        boolean success = true;
        handleStopInterrupt();
        logger.debug("[Study = " + task.getContext().getStudy() + "] handleReceiveFiles - start. filesSourceDir= [" + filesSourceDir +
                "] from server = [" + serverAddress + "] tmpInputFilesDir = [" + tmpInputFilesDir + "]");

        File inputDir = new File(this.tmpInputFilesDir);
        if (inputDir.exists()) {
            logger.error("[Study = " + task.getContext().getStudy() + "] task input Dir already exists...");
            return false;
        }
        handleStopInterrupt();
        boolean result = false;
        try{
            inputDir.mkdir();
            result = true;
        } catch(SecurityException se){
            logger.error("[Study = " + task.getContext().getStudy() + "] Failed to create task input Dir " + tmpInputFilesDir);
            return false;
        }
        if(!result) {
            logger.error("[Study = " + task.getContext().getStudy() + "] Failed to create task input Dir " + tmpInputFilesDir);
            return false;
        }
        handleStopInterrupt();
        SftpManager filesClient = null;
        try {
            filesClient = new SftpManager(sshConnectionProperties, serverAddress, filesSourceDir, tmpInputFilesDir);
            filesClient.getFilesFromServer();
        } catch (NetworkException e) {
            logger.error("[Study = " + task.getContext().getStudy() + "] Failed receiving files from server " + e.getMessage(), e);
            return false;
        }
        return success;
    }

    private void handleBaseUnitRequest(String machineWorkspaceDir) throws Throwable {
        logger.debug("[Study = " + task.getContext().getStudy() + "] handleBaseUnitRequest - start. task ID = [" + task.getId() +"]" );
        handleStopInterrupt();
        if (!handleReceiveFiles(task.getInputPath())){
            handleStopInterrupt();
            updateTaskFailure(task);
            return;
        }
        File outputDir = new File(tmpOutputFilesDir);
        if (outputDir.exists()) {
            handleStopInterrupt();
            logger.error("[Study = " + task.getContext().getStudy() + "] task output Dir already exists... " + tmpOutputFilesDir);
            updateTaskFailure(task);
            return;
        }
        boolean result;
        try {
            handleStopInterrupt();
            outputDir.mkdir();
            result = true;
        } catch (SecurityException se){
            handleStopInterrupt();
            logger.error("[Study = " + task.getContext().getStudy() + "] Failed to create task output Dir " + tmpOutputFilesDir);
            updateTaskFailure(task);
            return;
        }
        if(!result) {
            handleStopInterrupt();
            logger.error("[Study = " + task.getContext().getStudy() + "] Failed to create task output Dir " + tmpOutputFilesDir);
            updateTaskFailure(task);
            return;
        }
        handleStopInterrupt();
        MachineController machineController = new MachineControllerImpl();
        boolean success = machineController.run(task, Paths.get(baseDir, unitsDir), Paths.get(baseDir), machineWorkspaceDir);
        if (success){
            handleStopInterrupt();
            // need to send the output files to the server.
            if (sendOutputFilesToServer()) {
                handleStopInterrupt();
                updateTaskCompleted(task);
            } else {
                handleStopInterrupt();
                updateTaskFailure(task);
            }
        } else {
            handleStopInterrupt();
            updateTaskFailure(task);
        }
        // delete local input & output dirs
        cleanLocalTempDirectories();
    }

    private void handleStopInterrupt() throws HandledInterruptedException {
        if (Thread.currentThread().isInterrupted()){
            logger.info("[Study = " + task.getContext().getStudy() + "] Thread: [" + Thread.currentThread().getName() + "] " +
                    "isInterrupted. Stopping ...");
            removeThreadFromCollection();
            cleanLocalTempDirectories();
            updateTaskStopped(task);
            throw new HandledInterruptedException("[Study = " + task.getContext().getStudy() + "] Safely stopped thread of task " + task.getId());
        }
    }

    private void handleStopInterrupt(boolean wasThrown) throws HandledInterruptedException {
        logger.info("[Study = " + task.getContext().getStudy() + "] Thread: [" + Thread.currentThread().getName() + "] " +
                    "isInterrupted. Stopping ...");
        removeThreadFromCollection();
        cleanLocalTempDirectories();
        updateTaskStopped(task);
        logger.info("[Study = " + task.getContext().getStudy() + "] Safely stopped thread of task " + task.getId());
    }

    private void cleanLocalTempDirectories() {
        if (!tmpInputFilesDir.equals(""))
            cleanLocalDir(tmpInputFilesDir);
        if (!tmpOutputFilesDir.equals(""))
            cleanLocalDir(tmpOutputFilesDir);
    }

    private void cleanLocalDir(String dir) {
        try {
            FileUtils.deleteDirectory(new File(dir));
        } catch (IOException e) {
            logger.error("Failed cleaning local directory " + dir, e);
        }
    }

    private boolean sendOutputFilesToServer() throws Throwable {
        boolean success = true;
        logger.info("sendOutputFilesToServer - start. filesSourceDir= [" + this.tmpOutputFilesDir +
                "] to server = [" + serverAddress + "] destination files dir = [" + task.getOutputPath() + "]" );
        handleStopInterrupt();
        SftpManager filesUploader;
        try {
            handleStopInterrupt();
            filesUploader = new SftpManager(sshConnectionProperties, serverAddress, task.getOutputPath(), tmpOutputFilesDir);
            handleStopInterrupt();
            filesUploader.uploadFilesToServer();
        } catch (NetworkException e) {
            logger.error("Failed uploading files to server " + e.getMessage(), e);
            return false;
        }
        return success;
    }

    private void updateTaskFailure(Task task) {
        updateTaskStatus(TaskStatus.FAILED);
    }

    private void updateTaskCompleted(Task task) {
        updateTaskStatus(TaskStatus.COMPLETED);
    }

    private void updateTaskStopped(Task task) {
        updateTaskStatus(TaskStatus.STOPPED);
    }

    private void updateTaskStatus(TaskStatus status) {
        logger.info("[Study = " + task.getContext().getStudy() + "] Sending task update to server. Task id = [" + task.getId() + "] status = ["+status.toString()+"]");
        final String QUEUE_NAME =  SystemConstants.UBONGO_SERVER_TASKS_STATUS_QUEUE;
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(serverAddress);
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            task.setStatus(status);
            RabbitData message = new RabbitData(task, MachineConstants.UPDATE_TASK_REQUEST);
            channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
            if (logger.isDebugEnabled()) {
                logger.debug(" [!] Sent '" + message.getMessage() + "'");
            }
            channel.close();
            connection.close();
        } catch (Exception e){
            logger.error("[Study = " + task.getContext().getStudy() + "] Failed sending task status to server. Task id = [" + task.getId() + "] Status = [" +
                    status.toString() + "] error: " + e.getMessage(), e);
        }
    }
}
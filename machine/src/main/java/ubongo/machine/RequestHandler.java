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
    private static Configuration configuration;

    private String unitsDir; // The directory where the units should be stored
    private String workspaceDir; // The directory where tmp run files should be stored
    private String serverAddress; // Address of the program server
    private RabbitData rabbitMessage;

    private String tmpInputFilesDir = "";
    private String tmpOutputFilesDir = "";

    private Task task;
    private String taskStudy;
    private int unitId = 0;

    public RequestHandler(String threadName, RabbitData rabbitMessage, String serverAddress,
                          String unitsDir, Configuration config, String workspaceDir) {
        super(threadName);
        this.unitsDir = unitsDir;
        this.workspaceDir = workspaceDir;
        this.serverAddress = serverAddress;
        this.rabbitMessage = rabbitMessage;
        configuration = config;
        logger.info("serverAddress = [" + serverAddress + "] " +
                    "unitsDir = [" + unitsDir + "] message = [" + rabbitMessage.getMessage() + "]");
    }

    @Override
    public void run() {
        try {
            this.task = rabbitMessage.getTask();
            this.taskStudy = task.getContext().getStudy();
            this.unitId = task.getUnit().getId();
            logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Parsed request = [" + rabbitMessage.getMessage() + " " + task.getId() + "]");
            if (rabbitMessage.getMessage().equals(MachineConstants.BASE_UNIT_REQUEST)) {
                this.tmpOutputFilesDir = Paths.get(workspaceDir, task.getId() + MachineConstants.OUTPUT_DIR_SUFFIX).toString();
                this.tmpInputFilesDir = Paths.get(workspaceDir, task.getId() + MachineConstants.INPUT_DIR_SUFFIX).toString();
                handleBaseUnitRequest(workspaceDir);
                removeThreadFromCollection();
            } else if (rabbitMessage.getMessage().equals(MachineConstants.KILL_TASK_REQUEST)) {
                handleKillRequest();
            }
        } catch (InterruptedException ie) {
            try {
                logger.debug("[Study = " + taskStudy + "] [Unit = " + unitId + "] InterruptedException - calling handleStopInterrupt");
                handleStopInterrupt(true);
            } catch (HandledInterruptedException e) {
                logger.debug("[Study = " + taskStudy + "] [Unit = " + unitId + "] HandledInterruptedException");
                // do nothing :)
            }
        } catch (HandledInterruptedException hie) {
            // do nothing :)
        } catch (Exception e) {
            logger.error("[Study = " + taskStudy + "]  [Unit = " + unitId + "] Failed handling request: " + e.getMessage(), e);
        } catch (Throwable throwable) {
            if (throwable instanceof InterruptedException) {
                logger.debug("(throwable instanceof InterruptedException");
                try {
                    handleStopInterrupt(true);
                } catch (HandledInterruptedException e) {
                    // do nothing :)
                }
            } else {
                logger.error("[Study = " + taskStudy + "] [Unit = " + unitId + "] Failed handling request: " + throwable.getMessage(), throwable);
            }
        }
    }

    private void removeThreadFromCollection() {
        String threadName = MachineServer.getThreadName(task);
        if (MachineServer.unitThreads.containsKey(threadName))
            MachineServer.unitThreads.remove(threadName);
    }


    private void handleKillRequest() {
        String threadName = MachineServer.getThreadName(task);
        if (MachineServer.unitThreads.containsKey(threadName)){
            Thread currTaskThread = MachineServer.unitThreads.get(threadName);
            currTaskThread.interrupt();
            logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Thread [" + currTaskThread.getName() + "] " +
                    " with id = [" +currTaskThread.getId() + "] was interrupted.");
        } else {
            logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Thread " + threadName + " is not running on the machine.");
            updateTaskStopped();
        }
    }

    private boolean handleReceiveFiles(String filesSourceDir) throws Throwable {
        boolean success = true;
        handleStopInterrupt();
        logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Start downloading input files. filesSourceDir= [" + filesSourceDir +
                "] from server = [" + serverAddress + "] tmpInputFilesDir = [" + tmpInputFilesDir + "]");

        File inputDir = new File(this.tmpInputFilesDir);
        if (inputDir.exists()) {
            logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] task tmp input Dir already exists...");
        } else {
            handleStopInterrupt();
            boolean result = false;
            try {
                inputDir.mkdir();
                result = true;
            } catch (SecurityException se) {
                logger.error("[Study = " + taskStudy + "] [Unit = " + unitId + "] Failed to create task tmp input Dir " + tmpInputFilesDir);
                return false;
            }
            if (!result) {
                logger.error("[Study = " + taskStudy + "] [Unit = " + unitId + "] Failed to create task tmp input Dir " + tmpInputFilesDir);
                return false;
            }
        }
        handleStopInterrupt();
        SftpManager filesClient = null;
        try {
            filesClient = new SftpManager(configuration.getSshConnectionProperties(), serverAddress,
                    filesSourceDir, tmpInputFilesDir, task);
            filesClient.getFilesFromServer();
        } catch (NetworkException e) {
            logger.error("[Study = " + taskStudy + "] [Unit = " + unitId + "] Failed receiving files from server " + e.getMessage(), e);
            return false;
        }

        File tmpInputDirectoryFile = new File(tmpInputFilesDir);
        handleStopInterrupt();
        if (tmpInputDirectoryFile.list().length == 0) {
            handleStopInterrupt();
            logger.error("[Study = " + taskStudy + "] [Unit = "+ unitId + "] No input files were find for unit. Please verify the input path " +
                    "in the unit configurations, including the regex.");
            return false;
        }
        return success;
    }

    private void handleBaseUnitRequest(String machineWorkspaceDir) throws Throwable {
        logger.info("[Study = " + taskStudy + "] [Unit = "+ unitId + "] Start handling unit. task ID = [" + task.getId() +"]" );
        handleStopInterrupt();
        if (!handleReceiveFiles(task.getInputPath())){
            handleStopInterrupt();
            updateTaskFailure();
            return;
        }
        File outputDir = new File(tmpOutputFilesDir);
        if (outputDir.exists()) {
            logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] task tmp output Dir already exists...");
        } else {
            boolean result;
            try {
                handleStopInterrupt();
                outputDir.mkdir();
                result = true;
            } catch (SecurityException se) {
                handleStopInterrupt();
                logger.error("[Study = " + taskStudy + "] [Unit = "+ unitId + "] Failed to create task tmp output Dir " + tmpOutputFilesDir);
                updateTaskFailure();
                return;
            }
            if (!result) {
                handleStopInterrupt();
                logger.error("[Study = " + taskStudy + "] [Unit = "+ unitId + "] Failed to create task tmp output Dir " + tmpOutputFilesDir);
                updateTaskFailure();
                return;
            }
        }
        handleStopInterrupt();
        MachineController machineController = new MachineControllerImpl();
        boolean success = machineController.run(task, Paths.get(unitsDir), machineWorkspaceDir);
        if (success){
            handleStopInterrupt();
            // need to send the output files to the server.
            if (sendOutputFilesToServer()) {
                handleStopInterrupt();
                updateTaskCompleted();
            } else {
                handleStopInterrupt();
                updateTaskFailure();
            }
        } else {
            handleStopInterrupt();
            updateTaskFailure();
        }
        // delete local input & output dirs
        cleanLocalTempDirectories();
    }

    private void handleStopInterrupt() throws HandledInterruptedException {
        if (Thread.currentThread().isInterrupted()){
            logger.info("[Study = " + taskStudy + "] [Unit = "+ unitId + "] Thread: [" + Thread.currentThread().getName() + "] " +
                    "was interrupted by stop task request. Stopping ...");
            removeThreadFromCollection();
            cleanLocalTempDirectories();
            updateTaskStopped();
            throw new HandledInterruptedException("[Study = " + taskStudy + "] [Unit = "+ unitId + "] Safely stopped thread of task " + task.getId());
        }
    }

    private void handleStopInterrupt(boolean wasThrown) throws HandledInterruptedException {
        logger.info("[Study = " + taskStudy + "]  [Unit = "+ unitId + "] Thread: [" + Thread.currentThread().getName() + "] " +
                    "was interrupted by stop task request. Stopping ...");
        removeThreadFromCollection();
        cleanLocalTempDirectories();
        updateTaskStopped();
        logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Safely stopped thread of task " + task.getId());
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
        logger.info("[Study = " + taskStudy + "] [Unit = "+ unitId + "] Start uploading output files to server. filesSourceDir= [" + this.tmpOutputFilesDir +
                "] to server = [" + serverAddress + "] destination files dir = [" + task.getOutputPath() + "]" );
        handleStopInterrupt();
        SftpManager filesUploader;
        try {
            handleStopInterrupt();
            filesUploader = new SftpManager(configuration.getSshConnectionProperties(), serverAddress,
                    task.getOutputPath(), tmpOutputFilesDir, task);
            handleStopInterrupt();
            filesUploader.uploadFilesToServer();
        } catch (NetworkException e) {
            logger.error("[Study = " + taskStudy + "] [Unit = "+ unitId + "] Failed uploading files to server " + e.getMessage(), e);
            return false;
        }
        return success;
    }

    private void updateTaskFailure() {
        updateTaskStatus(TaskStatus.FAILED);
    }

    private void updateTaskCompleted() {
        updateTaskStatus(TaskStatus.COMPLETED);
    }

    private void updateTaskStopped() {
        updateTaskStatus(TaskStatus.STOPPED);
    }

    private void updateTaskStatus(TaskStatus status) {
        logger.info("[Study = " + taskStudy + "] [Unit = "+ unitId + "] Sending task update to server. Task id = [" + task.getId() + "] status = ["+status.toString()+"]");
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
            logger.error("[Study = " + taskStudy + "] [Unit = "+ unitId + "] Failed sending task status to server. Task id = [" + task.getId() + "] Status = [" +
                    status.toString() + "] error: " + e.getMessage(), e);
        }
    }
}
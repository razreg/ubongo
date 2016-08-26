package ubongo.machine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.constants.MachineConstants;
import ubongo.common.datatypes.Task;
import ubongo.common.datatypes.unit.Unit;
import ubongo.common.datatypes.unit.UnitParameter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class MachineControllerImpl implements MachineController {

    private static Logger logger = LogManager.getLogger(MachineControllerImpl.class);
    private String taskStudy;
    private int unitId;

    @Override
    public boolean run(Task task, Path unitsDir, String machineWorkspaceDir) throws InterruptedException {
        taskStudy = task.getContext().getStudy();
        unitId = task.getUnit().getId();
        Path outputDirectory = Paths.get(machineWorkspaceDir, task.getId() + MachineConstants.OUTPUT_DIR_SUFFIX);
        logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] outputDir= " + outputDirectory);

        Runtime runtime = Runtime.getRuntime();
        String[] command = getProcessCommand(task, outputDirectory, machineWorkspaceDir);
        Process p = null;
        try {
            boolean done = false;
            logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Executing unit Bash (and Matlabs that will be generated during execution)..." +
                    "\nWait for log updates when execution completed.");

            p = runtime.exec(command, null, new File(unitsDir.toString()));
            while (!done) {
                handleStopInterrupt(task);
                try {
                    p.exitValue();
                    done = true;
                } catch (IllegalThreadStateException e) {
                    // This exception will be thrown only if the process is still running
                    // because exitValue() will not be a valid method call yet...
                }
            }
            handleStopInterrupt(task);
            logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Unit completed successfully ." + getUnitOutput(p,task));
        } catch (IOException e) {
            handleStopInterrupt(task);
            if (p != null)
                logger.error("[Study = " + taskStudy + "] [Unit = "+ unitId +"] Failed running unit: " + getUnitErrors(p, task));
            else
                logger.error("[Study = " + taskStudy + "]  [Unit = "+ unitId +"] Failed running unit: " + e.getMessage());
            return false;
        }
        handleStopInterrupt(task);
        File outputDirectoryFile = new File(outputDirectory.toString());
        handleStopInterrupt(task);
        if (outputDirectoryFile.list().length == 0) {
            handleStopInterrupt(task);
            logger.error("[Study = " + taskStudy + "] [Unit = " + unitId + "] Unit completed, but output directory is empty : " + outputDirectory.toString()
                + "\nFor Matlab execution logs and for Matlab automated generated scripts, " +
                    "\nplease refer to the files that ends with task_" + task.getId() + ".m, task_" + task.getId() + ".txt in the following path: \n" +
                    Paths.get(unitsDir.toString(), "bashTmp").toString() );
            String bashErr = getUnitErrors(p, task);
            if (!bashErr.isEmpty())
                logger.error("[Study = " + taskStudy + "]  [Unit = " + unitId + "] Bash execution errors: " + bashErr);
            return false;
        }
        return true;
    }

    private String getUnitOutput(Process process, Task task) {
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder builder = new StringBuilder();
        String line = null;
        try {
            while ( (line = reader.readLine()) != null) {
                builder.append(line);
                builder.append(System.getProperty("line.separator"));
            }
        } catch (IOException e) {
            logger.error("[Study = " + taskStudy + "] [Unit = " + unitId + "] Failed receiving unit bash execution output : " + e.getMessage());
        }
        return builder.toString();
    }

    private String getUnitErrors(Process process, Task task) {
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getErrorStream()));
        StringBuilder builder = new StringBuilder();
        String line = null;
        try {
            while ( (line = reader.readLine()) != null) {
                builder.append(line);
                builder.append(System.getProperty("line.separator"));
            }
        } catch (IOException e) {
            logger.error("[Study = " + taskStudy + "] [Unit = " + unitId + "] Failed receiving unit bash execution errors : " + e.getMessage());
        }
        return builder.toString();
    }

    private String[] getProcessCommand(Task task, Path outputDirectory, String machineWorkspaceDir) {
        String inputDir = Paths.get(machineWorkspaceDir, task.getId() + MachineConstants.INPUT_DIR_SUFFIX).toString();
        Path inputDirectory = Paths.get(inputDir);
        String unitExecutable = Unit.getUnitBashFileName(task.getUnit().getId());
        List<UnitParameter> params = task.getUnit().getParameters();
        int paramsNum = 1 + 2 + params.size();
        String[] bashCommand = new String[paramsNum+1]; // extra param is TASK_ID
        bashCommand[0] = unitExecutable;
        bashCommand[1] = Integer.toString(task.getId());
        bashCommand[2] = inputDirectory.toString();
        bashCommand[3] = outputDirectory.toString();
        logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Unit information: Executable = " + unitExecutable + " tmpInputDir = " + inputDirectory.toString() +
                " tmpOutputDir = " + outputDirectory.toString());
        if (params.isEmpty())
            logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Unit has no arguments.");
        else
            logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Unit arguments:");
        int i = 4;
        for (UnitParameter unitParam : params) {
            bashCommand[i] = unitParam.getValue();
            logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Arg = [" + bashCommand[i] + "]");
            i++;
        }
        return bashCommand;
    }


    private void handleStopInterrupt(Task task) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()){
            logger.debug("[Study = " + task.getContext().getStudy() + "] [Unit = " + task.getUnit().getId() + "] Unit received interrupt exception");
            throw new InterruptedException("Received interrupt exception.");
        }
    }

}

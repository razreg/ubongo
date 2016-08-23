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

    @Override
    public boolean run(Task task, Path unitsDir, Path baseDir, String machineWorkspaceDir) throws InterruptedException {
        String outputDir = machineWorkspaceDir + File.separator + task.getId() + MachineConstants.OUTPUT_DIR_SUFFIX;

        logger.debug("[Study = " + task.getContext().getStudy() + "] outputDir= " + outputDir);
        Path outputDirectory = Paths.get(baseDir.toString(), outputDir);

        Runtime runtime = Runtime.getRuntime();
        String[] command = getProcessCommand(task, baseDir, outputDirectory, machineWorkspaceDir);
        Process p = null;
        try {
            boolean done = false;
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
            logger.info("[Study = " + task.getContext().getStudy() + "] Unit "+ task.getUnit().getId()+" completed successfully : " + getUnitOutput(p,task));
        } catch (IOException e) {
            handleStopInterrupt(task);
            if (p != null)
                logger.error("[Study = " + task.getContext().getStudy() + "] Failed running unit: " + getUnitErrors(p, task));
            else
                logger.error("[Study = " + task.getContext().getStudy() + "] Failed running unit: " + e.getMessage());
            return false;
        }
        handleStopInterrupt(task);
        File outputDirectoryFile = new File(outputDirectory.toString());
        handleStopInterrupt(task);
        if (outputDirectoryFile.list().length == 0) {
            handleStopInterrupt(task);
            logger.error("[Study = " + task.getContext().getStudy() + "] Unit completed, but output directory is empty : " + outputDirectory.toString());
            logger.error("[Study = " + task.getContext().getStudy() + "] Bash Errors: " + getUnitErrors(p, task));
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
            logger.error("[Study = " + task.getContext().getStudy() + "] Failed receiving unit output : " + e.getMessage());
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
            logger.error("[Study = " + task.getContext().getStudy() + "] Failed receiving unit errors : " + e.getMessage());
        }
        return builder.toString();
    }

    private String[] getProcessCommand(Task task, Path baseDir, Path outputDirectory, String machineWorkspaceDir) {
        String inputDir = machineWorkspaceDir + File.separator + task.getId() + MachineConstants.INPUT_DIR_SUFFIX;
        Path inputDirectory = Paths.get(baseDir.toString(), inputDir);
        String unitExecutable = Unit.getUnitBashFileName(task.getUnit().getId());
        List<UnitParameter> params = task.getUnit().getParameters();
        int paramsNum = 1 + 2 + params.size();
        String[] bashCommand = new String[paramsNum+1]; // extra param is TASK_ID
        bashCommand[0] = unitExecutable;
        bashCommand[1] = Integer.toString(task.getId());
        bashCommand[2] = inputDirectory.toString();
        bashCommand[3] = outputDirectory.toString();
        if (logger.isDebugEnabled()) {
            logger.debug("[Study = " + task.getContext().getStudy() + "] Unit information: Executable = " + unitExecutable + " InputDir = " + inputDirectory.toString() +
                    " OutputDir = " + outputDirectory.toString());
            logger.debug("[Study = " + task.getContext().getStudy() + "] Unit arguments:");
            int i = 4;
            for (UnitParameter unitParam : params) {
                bashCommand[i] = unitParam.getValue();
                logger.debug("[Study = " + task.getContext().getStudy() + "] Arg = [" + bashCommand[i] + "]");
                i++;
            }
        }
        return bashCommand;
    }


    private void handleStopInterrupt(Task task) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()){
            logger.debug("[Study = " + task.getContext().getStudy() + "] Unit received interrupt exception");
            throw new InterruptedException("Received interrupt exception.");
        }
    }

}

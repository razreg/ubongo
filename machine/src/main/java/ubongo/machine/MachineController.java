package ubongo.machine;

import ubongo.common.datatypes.MachineStatistics;
import ubongo.common.datatypes.Task;

import java.nio.file.Path;

/**
 * MachineController is run on each machine and manages the units running on it.
 * Every request to the machine passes through the MachineController and it
 * communicates with the ExecutionProxy via HTTP
 */
public interface MachineController {

    /**
     * The machine may gather performance metrics throughout its runtime or per request.
     * A call to getStatistics packages the metrics into a MachineStatistics object.
     * @return MachineStatistics with the performance metrics of the underlying machine
     */
    MachineStatistics getStatistics();

    /**
     * A request to run a certain unit on the machine. If the request is valid,
     * this will trigger the execution of a unit on the underlying machine
     * A response regarding the completion of the unit's operation will be returned.
     * @param task is the task for which the running request was sent.
     *             It includes all the settings the specific unit may require to run.
     * @param unitsDir is the directory where the unit files are stored on the machine
     * @param machineWorkspaceDir
     * @return true iff run ended successfully
     */
    boolean run(Task task, Path unitsDir, Path baseDir, String machineWorkspaceDir) throws InterruptedException;

}

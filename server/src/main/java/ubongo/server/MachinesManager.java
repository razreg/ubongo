package ubongo.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.datatypes.Machine;
import ubongo.common.datatypes.MachineStatistics;
import ubongo.persistence.Persistence;
import ubongo.persistence.PersistenceException;
import ubongo.server.exceptions.MachinesManagementException;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MachinesManager {

    private static Logger logger = LogManager.getLogger(MachinesManager.class);
    private final ScheduledExecutorService statisticsUpdateScheduler = Executors.newScheduledThreadPool(1);
    private List<Machine> machines;
    private ExecutionProxy executionProxy;
    private Persistence persistence;

    MachinesManager(List<Machine> machines, ExecutionProxy executionProxy, Persistence persistence) {
        this.persistence = persistence;
        this.executionProxy = executionProxy;
        this.machines = machines;
    }

    public void start() throws PersistenceException {
        persistence.saveMachines(machines);
        initPeriodicalStatisticsUpdate(2, TimeUnit.HOURS);
    }

    public void stop() {
        statisticsUpdateScheduler.shutdownNow();
    }

    public Machine getAvailableMachine() throws MachinesManagementException {
        Machine mostAvailableMachine = null;
        float availability = 0;
        for (Machine machine: machines) {
            double currentAvailability = machine.getMachineStatistics().getAvailabilityScore();
            if (currentAvailability > availability) {
                mostAvailableMachine = machine;
            }
        }
        if (mostAvailableMachine == null) {
            // should never happen! machines list should never be null or empty.
            throw new MachinesManagementException(
                    "No machines found. Please make sure machines are configured in the server.");
        }
        return mostAvailableMachine;
    }

    private void initPeriodicalStatisticsUpdate(int interval, TimeUnit intervalUnits) {
        statisticsUpdateScheduler.scheduleAtFixedRate(() -> {
            for (Machine machine: machines) {
                MachineStatistics machineStatistics = executionProxy.getStatistics(machine);
                machine.setConnected(machineStatistics != null);
                machine.setStatistics(machineStatistics);
                try {
                    persistence.updateMachine(machine);
                } catch (PersistenceException e) {
                    logger.error("Failed to update machine in DB.", e);
                }
            }
        }, 0, interval, intervalUnits);
    }
}

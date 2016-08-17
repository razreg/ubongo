package ubongo.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.datatypes.Machine;
import ubongo.persistence.Persistence;
import ubongo.persistence.PersistenceException;
import ubongo.server.exceptions.MachinesManagementException;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

public class MachinesManager {

    private static final int SECONDS_BETWEEN_HEARTBEAT_CYCLES = 60;

    private static Logger logger = LogManager.getLogger(MachinesManager.class);
    private final ScheduledExecutorService statisticsUpdateScheduler = Executors.newScheduledThreadPool(1);
    private List<Machine> machines;
    private Persistence persistence;

    private int counter = 0;

    MachinesManager(List<Machine> machines, Persistence persistence) {
        this.persistence = persistence;
        this.machines = machines;
    }

    public void setMachines(List<Machine> machines) {
        this.machines = machines;
    }

    public void start() throws PersistenceException {
        persistence.saveMachines(machines);
    }

    public void stop() {
        statisticsUpdateScheduler.shutdownNow();
    }

    public Machine getAvailableMachine() throws MachinesManagementException {
        // TODO remove the loop - this is a placeholder so that the server will send tasks to a machine
        for (Machine machine : machines) {
            if (machine.getId() == 1) {
                return machine;
            }
        }

        final Timestamp oldTime =
                new Timestamp(new Date().getTime() - 1000 * SECONDS_BETWEEN_HEARTBEAT_CYCLES * 2);
        List<Machine> machinesPool = machines.stream()
                .filter(m -> m.isActive() && m.isConnected() &&
                        m.getLastHeartbeat() != null && m.getLastHeartbeat().after(oldTime))
                .collect(Collectors.toList());
        if (machinesPool.isEmpty()) {
            throw new MachinesManagementException("No available machines");
        }
        counter = (counter + 1) % Integer.MAX_VALUE;
        return machinesPool.get(counter % machinesPool.size());
    }

    private void heartbeatListener() {
        // TODO Shelly's implementation. At some point we have a machine and from that point:
        Machine machine = new Machine(); // not really new - the same machine that corresponds to the heartbeat sender
        machine.setConnected(true); // received heartbeat
        machine.setLastHeartbeat(new Timestamp(new Date().getTime()));
        try {
            persistence.updateMachine(machine); // updates the heartbeat in the DB
        } catch (PersistenceException e) {
            logger.error("Failed to store heartbeat in DB for "
                    + machine.getHost() + " (ID = " + machine.getId() + ")", e);
        }
    }
}

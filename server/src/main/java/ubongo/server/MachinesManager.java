package ubongo.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.Utils;
import ubongo.common.datatypes.Machine;
import ubongo.persistence.HeartbeatSender;
import ubongo.persistence.Persistence;
import ubongo.persistence.exceptions.PersistenceException;
import ubongo.persistence.db.DBConstants;
import ubongo.server.exceptions.MachinesManagementException;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MachinesManager {

    private static final int SECONDS_BETWEEN_HEARTBEAT_CYCLES = 60;

    private static Logger logger = LogManager.getLogger(MachinesManager.class);
    private final ScheduledExecutorService serverHeartbeatScheduler = Executors.newScheduledThreadPool(1);
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
        Machine server = createServerMachine();
        List<Machine> machinesCopy = machines.stream().collect(Collectors.toList());
        machinesCopy.add(0, server);
        if (logger.isInfoEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Removing machines from database and saving machines:\n");
            machinesCopy.forEach(m -> sb.append(Utils.concatStrings("\tID=[",
                        m.getId(),"] Host=[",
                        m.getHost(), "], Description=[",
                        m.getDescription(), "]")));
            logger.info(sb.toString());
        }
        persistence.saveMachines(machinesCopy);
        logger.debug("Starting the heartbeat sender for the main server");
        final Runnable heartbeatSender = new HeartbeatSender(persistence, DBConstants.SERVER_ID);
        serverHeartbeatScheduler.scheduleAtFixedRate(heartbeatSender, 0, 60, TimeUnit.SECONDS);
    }

    private Machine createServerMachine() {
        Machine server = new Machine();
        server.setId(DBConstants.SERVER_ID);
        server.setHost("<server>");
        server.setDescription("server");
        return server;
    }

    public void stop() {
        serverHeartbeatScheduler.shutdownNow();
    }

    public Machine getAvailableMachine() throws MachinesManagementException {
        try {
            machines = persistence.getAllMachines(false);
        } catch (PersistenceException e) {
            throw new MachinesManagementException("Failed to retrieve machines from DB.", e);
        }
        final Timestamp oldTime =
                new Timestamp(new Date().getTime() - 1000 * SECONDS_BETWEEN_HEARTBEAT_CYCLES * 5);
        List<Machine> machinesPool = machines.stream()
                .filter(m -> m.isActive() && m.isConnected() &&
                        m.getLastHeartbeat() != null && m.getLastHeartbeat().after(oldTime))
                .collect(Collectors.toList());
        if (machinesPool.isEmpty()) {
            throw new MachinesManagementException("No available machines");
        }
        counter = (counter + 1) % Integer.MAX_VALUE;
        Machine selected = machinesPool.get(counter % machinesPool.size());
        if (logger.isDebugEnabled()) {
            String msg = Utils.concatStrings("Received request for available machine. Found: ", machinesPool.stream()
                    .map(m -> m.getDescription() + " (ID=" + m.getId() + ")")
                    .reduce((a, b) -> a + ", " + b), ". Returning: ", selected.getId());
            logger.debug(msg);
        }
        return selected;
    }
}

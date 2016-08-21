package ubongo.persistence;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.datatypes.Machine;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

public class HeartbeatSender implements Runnable {

    private static Logger logger = LogManager.getLogger(HeartbeatSender.class);
    private Persistence persistence;
    private Machine machine;

    public HeartbeatSender(Persistence persistence, int machineId) {
        this.persistence = persistence;
        this.machine = new Machine();
        machine.setId(machineId);
        machine.setConnected(true);
    }

    public HeartbeatSender(Persistence persistence, String host) throws PersistenceException {
        if (host == null) {
            throw new PersistenceException("Machine host address for heartbeat cannot be null");
        }
        this.persistence = persistence;
        this.machine = new Machine();
        List<Machine> machines = persistence.getAllMachines(false);
        boolean idSet = false;
        for (Machine m : machines) {
            if (host.equals(m.getHost())) {
                machine.setId(m.getId());
                idSet = true;
                break;
            }
        }
        if (!idSet) {
            throw new PersistenceException("Machine host address cannot be resolved to any known machine");
        }
        machine.setConnected(true);
    }

    @Override
    public void run() {
        machine.setLastHeartbeat(new Timestamp(new Date().getTime()));
        try {
            persistence.updateMachine(machine); // updates the heartbeat in the DB
        } catch (PersistenceException e) {
            logger.error("Failed to store heartbeat in DB for "
                    + machine.getHost() + " (ID = " + machine.getId() + ")", e);
        }
    }
}

package ubongo.persistence;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.datatypes.Machine;
import ubongo.persistence.exceptions.PersistenceException;

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
        machine.setHost(host);
        machine.setId(-1);
        machine.setConnected(true);
    }

    @Override
    public void run() {
        machine.setLastHeartbeat(new Timestamp(new Date().getTime()));
        try {
            boolean idSet = true;
            if (machine.getId() < 0) {
                idSet = resolveHost();
            }
            if (idSet) {
                persistence.updateMachine(machine); // updates the heartbeat in the DB
            }
        } catch (PersistenceException e) {
            logger.error("Failed to store heartbeat in DB for "
                    + machine.getHost() + " (ID = " + machine.getId() + ")", e);
        }
    }

    private boolean resolveHost() throws PersistenceException {
        boolean idSet = false;
        List<Machine> machines = persistence.getAllMachines(false);
        for (Machine m : machines) {
            if (machine.getHost().equals(m.getHost())) {
                machine.setId(m.getId());
                idSet = true;
                break;
            }
        }
        if (!idSet) {
            logger.warn("Machine host address [" + machine.getHost()
                    + "] cannot be resolved to any known machine");
        }
        return idSet;
    }
}

package ubongo.machine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import ubongo.common.datatypes.MachineStatistics;

@Deprecated
public class MachinePerformanceSampler implements Runnable {

    private static Logger logger = LogManager.getLogger(MachinePerformanceSampler.class);
    private MachineStatistics machineStatistics;

    public MachinePerformanceSampler(MachineStatistics machineStatistics) {
        this.machineStatistics = machineStatistics;
    }

    @Override
    public void run() {
        Sigar sigar = new Sigar();
        try {
            double totalCpuUsagePercent = sigar.getCpuPerc().getCombined();
            machineStatistics.updateCpuUsage(totalCpuUsagePercent);
            machineStatistics.updateMemoryUsage(sigar.getMem().getUsedPercent() / 100.0);
            if (logger.isDebugEnabled()) {
                logger.debug("CPU: " + Math.round(machineStatistics.getCpuUsage() * 10000.0) / 100.0 + "%" +
                        " Mem: " + Math.round(machineStatistics.getMemoryUsage() * 10000.0) / 100.0 + "%");
            }
        } catch (SigarException e) {
            logger.error("Failed to sample machine performance. Details: " + e.getMessage());
        } finally {
            sigar.close();
        }
    }
}

package ubongo.common.datatypes;

import java.io.Serializable;

/**
 * MachineStatistics encapsulated all performance metrics of a machine
 * (e.g., CPU average consumption).
 */
public class MachineStatistics implements Serializable {

    private static final double alpha = 0.1;

    int runningTasksNum;
    double cpuUsage;
    double memoryUsage;

    public MachineStatistics(int runningTasksNum) {
        this.cpuUsage = 0;
        this.memoryUsage = 0.5;
        this.runningTasksNum = runningTasksNum;
    }

    public void updateCpuUsage(double current) {
        cpuUsage = alpha * current + (1 - alpha) * cpuUsage;
    }

    public void updateMemoryUsage(double current) {
        memoryUsage = alpha * current + (1 - alpha) * memoryUsage;
    }

    public double getCpuUsage() {
        return cpuUsage;
    }

    public double getMemoryUsage() {
        return memoryUsage;
    }

    public int getNumOfRunningTasks() {
        return runningTasksNum;
    }

    /**
     * returns a single score between 0 and 1, measuring the availability of a machine
     */
    public double getAvailabilityScore() {
        return Math.sqrt(0.5 * (Math.pow(1 - memoryUsage, 2) + Math.pow(1 - cpuUsage, 2)));
    }

}

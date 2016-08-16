package ubongo.rest;

import ubongo.common.constants.MachineConstants;
import ubongo.common.datatypes.unit.Unit;
import ubongo.common.datatypes.unit.UnitParameter;
import ubongo.persistence.Configuration;

import java.io.PrintWriter;
import java.util.List;

public class UnitAdder {
    final static int BASE_PARAMS = 3;

    public static void generateBashFile(Unit unit, String unitBashPath) throws Exception {
        PrintWriter writer = new PrintWriter(unitBashPath, "UTF-8");
        addBashParams(unit, writer);
        addMatlabLoopPrefix(writer);
        addMatlabParams(unit, writer);
        addMatlabLoopSuffix(unit, writer);
        writer.close();
    }

    private static void addBashParams(Unit unit, PrintWriter writer) {
        List<UnitParameter> unitParams = unit.getParameters();
        writer.println("TASK_ID=\"$1\"");
        writer.println("INPUT_DIR=\"$2\"");
        writer.println("OUTPUT_DIR=\"$3\"");

        int i = 1;
        for (UnitParameter param : unitParams){
            writer.println(param.getName().toUpperCase() + "=\"$" + (i+BASE_PARAMS) + "\"");
            i++;
        }
        writer.println("\n");
    }

    private static void addMatlabLoopPrefix(PrintWriter writer) throws Exception{
        writer.println("for currfilename in ${INPUT_DIR}/*");
        writer.println("do");
        writer.println("\t" + "currfilename=$(basename \"$currfilename\")");
        writer.println("\t" + "parmstr=\"${currfilename}\"");
        writer.println("\t" + "filename=\"bashTmp/run_${parmstr}_task_${TASK_ID}.m\"");
        writer.println("\t" + "logfile=\"bashTmp/log_${parmstr}_task_${TASK_ID}.txt\"");
        writer.println("\n");
        writer.println("\t" + "rm -f \"${filename}\"");
        writer.println("\t" + "touch \"${filename}\"");
        writer.println("\t" + "# Write to Matlab file");

        addMatlabDependencies(writer);

        writer.println("\t" + "echo \"input_dir ='${INPUT_DIR}';\" >> ${filename}");
        writer.println("\t" + "echo \"output_dir ='${OUTPUT_DIR}';\" >> ${filename}");
        writer.println("\t" + "echo \"input_file_name ='${currfilename}';\" >> ${filename}");
    }

    private static void addMatlabLoopSuffix(Unit unit, PrintWriter writer) {
        writer.println("\t" + "echo \"$(cat "+ Unit.getUnitMatlabFileName(unit.getId()) + ")\" >> \"${filename}\" ");
        writer.println("\n");
        writer.println("\t" + "echo \"matlab -nojvm -nodisplay -nosplash < ${filename} >&! ${logfile}\"");
        writer.println("\t" + "nohup matlab < \"${filename}\" >& \"${logfile}\" &");
        writer.println("done");
        writer.println("wait");
    }

    private static void addMatlabParams(Unit unit, PrintWriter writer) {
        List<UnitParameter> unitParams = unit.getParameters();
        int i = 1;
        for (UnitParameter param : unitParams){
            writer.println("\t" + "echo \"" + param.getName().toLowerCase() + "='${" + param.getName().toUpperCase() + "}';\" >> ${filename}");
            i++;
        }
    }

    private static void addMatlabDependencies(PrintWriter writer) throws Exception{
        String configPath = System.getProperty(MachineConstants.CONFIG_PATH);
        Configuration configuration = Configuration.loadConfiguration(configPath);
        List<String> dependencies = configuration.getUnitsMainProperties().getMatlabDepenencies();
        for (String dependency : dependencies)
            writer.println("\t" + "echo \"" + dependency +";\" >> ${filename}");
    }
}
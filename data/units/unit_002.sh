TASK_ID="$1"
INPUT_DIR="$2"
OUTPUT_DIR="$3"
LOCUTOFF="$4"
HICUTOFF="$5"
REVFILT="$6"
USEFFT="$7"
PLOTFREQZ="$8"
FIRTYPE="$9"
CAUSAL="$10"


for currfilename in ${INPUT_DIR}/*
do
        currfilename=$(basename "$currfilename")
        parmstr="${currfilename}"
        filename="bashTmp/run_${parmstr}_task_${TASK_ID}.m"
        logfile="bashTmp/log_${parmstr}_task_${TASK_ID}.txt"


        rm -f "${filename}"
        touch "${filename}"
        # Write to Matlab file
        echo "addpath(genpath('/specific/netapp5/hezi/shellybar/units/eeglab13_0_0b/eeglab13_0_0b'));" >> ${filename}
        echo "rmpath('/specific/netapp5/hezi/shellybar/units/eeglab13_0_0b/eeglab13_0_0b/functions/octavefunc/signal');" >> ${filename}
        echo "input_dir ='${INPUT_DIR}';" >> ${filename}
        echo "output_dir ='${OUTPUT_DIR}';" >> ${filename}
        echo "input_file_name ='${currfilename}';" >> ${filename}
        echo "locutoff='${LOCUTOFF}';" >> ${filename}
        echo "hicutoff='${HICUTOFF}';" >> ${filename}
        echo "revfilt='${REVFILT}';" >> ${filename}
        echo "usefft='${USEFFT}';" >> ${filename}
        echo "plotfreqz='${PLOTFREQZ}';" >> ${filename}
        echo "firtype='${FIRTYPE}';" >> ${filename}
        echo "causal='${CAUSAL}';" >> ${filename}
        echo "$(cat unit_002.m)" >> "${filename}"


        echo "matlab -nojvm -nodisplay -nosplash < ${filename} >&! ${logfile}"
        nohup matlab < "${filename}" >& "${logfile}" &
done
wait

TASK_ID="$1"
INPUT_DIR="$2"
OUTPUT_DIR="$3"
REF_ELECS="$4"


for currfilename in ${INPUT_DIR}/*
do
        currfilename=$(basename "$currfilename")
        parmstr="${currfilename}"
        filename="bashTmp/run_${parmstr}_task_${TASK_ID}.m"
        logfile="bashTmp/log_${parmstr}_task_${TASK_ID}.txt"


        rm -f "${filename}"
        touch "${filename}"
        # Write to Matlab file
        echo "addpath(genpath('/specific/netapp5/hezi/EEGPipelineSystem/eeglab13_0_0b/eeglab13_0_0b'));" >> ${filename}
        echo "rmpath('/specific/netapp5/hezi/EEGPipelineSystem/eeglab13_0_0b/eeglab13_0_0b/functions/octavefunc/signal');" >> ${filename}
        echo "input_dir ='${INPUT_DIR}';" >> ${filename}
        echo "output_dir ='${OUTPUT_DIR}';" >> ${filename}
        echo "input_file_name ='${currfilename}';" >> ${filename}
        echo "ref_elecs='${REF_ELECS}';" >> ${filename}
        echo "$(cat unit_003.m)" >> "${filename}"


        echo "matlab -nojvm -nodisplay -nosplash < ${filename} >&! ${logfile}"
        nohup matlab < "${filename}" >& "${logfile}" &
done
wait

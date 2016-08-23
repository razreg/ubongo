
INPUT_DIR="$1"
OUTPUT_DIR="$2"

for currfilename in ${INPUT_DIR}/*
do
    currfilename=$(basename "$currfilename")
    parmstr="${currfilename}"
    filename="bashTmp/run_${parmstr}.m"
    logfile="bashTmp/log_${parmstr}.txt"
	
    rm -f "${filename}"
    touch "${filename}"
    # Write to Matlab file
	echo "inputFileName='${currfilename}';" >> ${filename}	
	echo "inputDir='${INPUT_DIR}';" >> ${filename}
	echo "outputDir='${OUTPUT_DIR}';" >> ${filename}
	echo "$(cat unit_004.m)" >> "${filename}"
         
	echo "matlab -nojvm -nodisplay -nosplash < ${filename} >&! ${logfile}"
      
	nohup matlab < "${filename}" >& "${logfile}" &
done
wait

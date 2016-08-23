file_source = fullfile(input_dir,input_file_name);
name= strsplit(input_file_name,'.');
name = name(1);
nameStr = name{1};
file_dest = fullfile(output_dir,[nameStr '_lowfilt' locutoff '_hifilt' hicutoff '.set']);
EEG  = pop_loadset(fullfile(input_dir,input_file_name));
EEG = pop_eegfilt( EEG, str2num(locutoff), str2num(hicutoff), [], str2num(revfilt), str2num(usefft), str2num(plotfreqz), firtype, str2num(causal));
pop_saveset(EEG, 'filename', file_dest);



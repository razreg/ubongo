file_source = fullfile(input_dir,input_file_name);
name= strsplit(input_file_name,'.');
name = name(1);
nameStr = name{1};
file_dest = fullfile(output_dir,[nameStr '.set']);
EEG  = pop_loadset(fullfile(input_dir,input_file_name));
EEG = pop_reref(EEG, ref_elecs);
pop_saveset(EEG, 'filename', file_dest);


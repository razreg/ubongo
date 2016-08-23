file_source = fullfile(input_dir,input_file_name);
name= strsplit(input_file_name,'.');
name = name(1);
nameStr = name{1};
mkdir(fullfile(output_dir,'AllData'));
file_dest = fullfile(output_dir,'AllData',[nameStr '.set']);
mkdir(fullfile(output_dir,'DataAfterManualRemoval'));
file_dest2 = fullfile(output_dir,'DataAfterManualRemoval',[nameStr '.set']);
EEG = pop_biosig(file_source);
pop_saveset(EEG, 'filename', file_dest);
pop_saveset(EEG, 'filename', file_dest2);


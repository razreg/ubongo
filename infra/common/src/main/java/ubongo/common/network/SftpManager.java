package ubongo.common.network;

import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ubongo.common.datatypes.Task;
import ubongo.common.exceptions.NetworkException;

import java.io.File;
import java.io.FilenameFilter;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SftpManager {

    private static Logger logger = LogManager.getLogger(SftpManager.class);
    private String remoteDir;
    private String localDir;
    private String machine;
    private String user;
    private String password;
    private String sftpUri;
    private FileSystemManager fsManager = null;
    private String taskStudy;
    private int unitId = 0;

    public SftpManager(SSHConnectionProperties sshProperties, String machine, String remoteDir, String localDir, Task task) {
        this.machine = machine;
        this.remoteDir = remoteDir;
        this.localDir = localDir;
        this.user = sshProperties.getUser();
        this.password = sshProperties.getPassword();
        this.sftpUri = "sftp://" + user + ":" + password +  "@" + machine + remoteDir + File.separator;
        this.taskStudy = task.getContext().getStudy();
        this.unitId = task.getUnit().getId();
        logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] SftpManager was initiated. [Machine=" + this.machine + "] [remoteDir = " + this.remoteDir + "] [localDir = " + this.localDir + "]");
    }

    /**
     * Receives files using SFTP.
     * Used for receiving files from the main files server to the machines.
     */
    public void getFilesFromServer() throws Throwable {
        Optional<String> targetFileNamesRegex = getFileRegexFromInputDirRegex();
        List<String> targetDirectoriesMatchingToRegexInPath = new ArrayList<>();
        handleStopInterrupt();
        try {
            String remoteDirMainPath = remoteDir;
            if (!remoteDir.endsWith(File.separator)){ // remoteDir path ends with regex
                remoteDirMainPath = remoteDir.substring(0, remoteDir.lastIndexOf(File.separator)); // get path until regex
            }
            logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Getting sub-directories for " + remoteDirMainPath);
            handleStopInterrupt();
            getTargetDirectoriesMatchingToRegex(targetDirectoriesMatchingToRegexInPath, remoteDirMainPath);
        } catch (FileSystemException e) {
            handleIfItCausedByInterrupt(e);
            String error = "Failed getting input files directories from regex";
            logger.error(error, e);
            throw new NetworkException(error);
        }
        for (String currRemotelDir : targetDirectoriesMatchingToRegexInPath) {
            logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Downloading files from : " + currRemotelDir);
            handleStopInterrupt();
            getFilesFromServerByDirectory(currRemotelDir, targetFileNamesRegex);
        }
    }

    private Optional<String> getFileRegexFromInputDirRegex() {
        if (remoteDir.endsWith(File.separator))
            return Optional.empty();
        String dirParts[] = remoteDir.split(File.separator);
        logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Input files regex : " + dirParts[dirParts.length - 1]);
        return Optional.of(dirParts[dirParts.length -1]);
    }

    private void getTargetDirectoriesMatchingToRegex(List<String> dirs, String mainDir) throws FileSystemException, InterruptedException {
        handleStopInterrupt();
        boolean endWithReg = false;
        logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] [getTargetDirectoriesMatchingToRegex] Current main dir: " + mainDir);
        if (mainDir.matches(".*?\\(.*?\\)$")) {
            endWithReg = true;
            if (logger.isDebugEnabled()) {
                logger.debug("[Study = " + taskStudy + "] [Unit = " + unitId + "] Current main dir: [" + mainDir + "] ends with Regex");
            }

        }
        String dirParts[] = mainDir.split(File.separator+"\\(.*?\\)"+File.separator);
        String prefixString = dirParts[0];
        if ((dirParts.length == 1) && (!endWithReg)){
            fsManager = VFS.getManager();
            FileSystemOptions opts = new FileSystemOptions();
            String dirToAddSftpUri = "sftp://" + user + ":" + password + "@" + machine + prefixString + File.separator;
            FileObject currDirObject = fsManager.resolveFile(dirToAddSftpUri, opts);
            File currDirFile = new File(currDirObject.getName().getPath());
            if ((currDirFile.list() != null) && (currDirFile.list().length > 0)) {
                logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Current path is not empty: " + prefixString + ". " +
                        "Adding this directory to the list of target directories to download files from.");
                dirs.add(prefixString);
            } else {
                logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Current path is empty. No files will be downloaded from this directory : " + prefixString);
            }
            return;
        }
        String dirPathRelativeToMainDir = "";
        for (int i = 1; i < dirParts.length; i++){
            dirPathRelativeToMainDir += dirParts[i] + File.separator;
        }
        if (dirPathRelativeToMainDir!="")
            dirPathRelativeToMainDir = dirPathRelativeToMainDir.substring(0, dirPathRelativeToMainDir.lastIndexOf(File.separator));

        logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Current sub directory, relative to main current directory: " + dirPathRelativeToMainDir);

        String currRegex = "";
        if (dirPathRelativeToMainDir == ""){ // end with regex
            prefixString = mainDir.substring(0, mainDir.indexOf("(") - 1);
            currRegex = mainDir.substring(mainDir.indexOf("("), mainDir.length());
        } else {
            currRegex = mainDir.substring(dirParts[0].length() + 1, mainDir.lastIndexOf(dirPathRelativeToMainDir));
        }
        if (currRegex.endsWith(File.separator))
            currRegex = currRegex.substring(0, currRegex.lastIndexOf(File.separator));
        logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Current regex: " + currRegex);
        String suffixString = mainDir.substring(mainDir.lastIndexOf(dirPathRelativeToMainDir));
        String currSftpUri = "sftp://" + user + ":" + password +  "@" + machine + prefixString + File.separator;
        fsManager = VFS.getManager();
        handleStopInterrupt();
        FileSystemOptions opts = new FileSystemOptions();
        FileObject localFileObject=fsManager.resolveFile(currSftpUri,opts);
        File file = new File(localFileObject.getName().getPath());
        String[] directories = file.list(new FilenameFilter() {
            @Override
            public boolean accept(File current, String name) {
                return new File(current, name).isDirectory();
            }
        });
        if (directories != null) {
            for (String currSubDir : directories) {
                logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Sub directory: " + currSubDir);
                handleStopInterrupt();
                if (currSubDir.matches(currRegex)) {
                    if ((currSubDir.contains("%")||(currSubDir.contains(".")))) {
                        continue;
                    }
                    String seperator = (suffixString == "") ? "" :  File.separator;
                    String currPath = prefixString + File.separator + currSubDir + seperator + suffixString;
                    logger.debug("[Study = " + taskStudy + "] [Unit = " + unitId + "] Sub path to get target directories from: " + currPath);
                    getTargetDirectoriesMatchingToRegex(dirs, currPath);
                } else {
                    logger.debug("[Study = " + taskStudy + "] [Unit = " + unitId + "] Directory " + currSubDir + " doesn't match pattern " + currRegex);
                }
            }
        }
    }

    private void getFilesFromServerByDirectory(String currRemotelDir, Optional<String> fileRegex) throws Throwable {
        handleStopInterrupt();
        String currSftpUri = "sftp://" + user + ":" + password +  "@" + machine + currRemotelDir + "/";
        try {
            FileSystemOptions opts = new FileSystemOptions();
            SftpFileSystemConfigBuilder.getInstance().setUserDirIsRoot(opts, false);
            fsManager = VFS.getManager();

            // List all the files in that directory.

            FileObject localFileObject=fsManager.resolveFile(currSftpUri,opts);

            FileObject[] children = localFileObject.getChildren();
            for ( int i = 0; i < children.length; i++ ){
                handleStopInterrupt();
                String fileName = children[ i ].getName().getBaseName();
                if ((fileName.contains("%"))) {
                    continue;
                }
                if ((fileRegex.isPresent()) && !(fileName.matches(fileRegex.get()))) {
                    logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "]File " + fileName + " doesn't match pattern " + fileRegex.get());
                    continue;
                }
                String filepath = localDir + File.separator  + fileName;
                File file = new File(filepath);
                FileObject localFile = fsManager.resolveFile(file.getAbsolutePath(),opts);
                FileObject remoteFile = fsManager.resolveFile(currSftpUri+ File.separator + fileName, opts);

                localFile.copyFrom(remoteFile, Selectors.SELECT_SELF);
                logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] File downloaded successfully: " + fileName);
            }
        } catch (FileSystemException ex) {
            handleIfItCausedByInterrupt(ex);
            logger.error("[Study = " + taskStudy + "] [Unit = " + unitId + "] sftp error", ex);
            throw new NetworkException(ex.getMessage());
        }
        return;
    }

    private void handleIfItCausedByInterrupt(Throwable e) throws Throwable {
        while (e != null){
            e = e.getCause();
            if ((e instanceof InterruptedException) || (e instanceof InterruptedIOException)) {
                logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] handleIfItCausedByInterrupt - throw");
                throw new InterruptedException(e.getMessage());
            }
        }
    }

    /**
     * Uploads files using SFTP.
     * Used for sending files from the machine to the main files server.
     */
    public void uploadFilesToServer() throws Throwable {
        try {
            handleStopInterrupt();
            FileSystemOptions opts = new FileSystemOptions();
            SftpFileSystemConfigBuilder.getInstance().setUserDirIsRoot(opts, false);
            fsManager = VFS.getManager();

            File folder = new File(localDir);
            recursiveUploadDirectory(opts, folder);
        } catch (FileSystemException ex) {
            handleIfItCausedByInterrupt(ex);
            logger.error("[Study = " + taskStudy + "] [Unit = " + unitId + "] sftp error", ex);
            throw new NetworkException(ex.getMessage());
        }
        return;
    }

    private void recursiveUploadDirectory(FileSystemOptions opts, File folder) throws InterruptedException, NetworkException, FileSystemException {
        File[] listOfToUploadFiles = folder.listFiles();
        logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] Directory to upload : " + folder);

        for (File fileToUpload : listOfToUploadFiles) {
            logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] File to upload: " + fileToUpload);
            handleStopInterrupt();
            if (!fileToUpload.exists()) {
                logger.error("[Study = " + taskStudy + "] [Unit = " + unitId + "] Error. Local file not found : " + fileToUpload.getAbsolutePath());
                throw new NetworkException("Error. Local file not found");
            }
            FileObject localFile = fsManager.resolveFile(fileToUpload.getAbsolutePath(),opts);
            String relativeLocalPath = new File(localDir).toURI().relativize(new File(fileToUpload.getAbsolutePath()).toURI()).getPath();
            FileObject remoteFile = fsManager.resolveFile(sftpUri + File.separator + relativeLocalPath, opts);
            remoteFile.copyFrom(localFile, Selectors.SELECT_SELF);
            logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] File uploaded successfully: " + relativeLocalPath);

            if (fileToUpload.listFiles() != null){
                recursiveUploadDirectory(opts, fileToUpload);
            }
        }
    }

    private void handleStopInterrupt() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()){
            logger.info("[Study = " + taskStudy + "] [Unit = " + unitId + "] sftp manager - Received interrupt exception.");
            throw new InterruptedException("Received interrupt exception.");
        }
    }

}

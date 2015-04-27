package uk.ac.ucl.cs.cmic.giftcloud.uploader;

import uk.ac.ucl.cs.cmic.giftcloud.util.ProgressHandleWrapper;
import org.apache.commons.lang.StringUtils;
import uk.ac.ucl.cs.cmic.giftcloud.dicom.FileCollection;
import uk.ac.ucl.cs.cmic.giftcloud.restserver.GiftCloudProperties;
import uk.ac.ucl.cs.cmic.giftcloud.uploadapp.GiftCloudAutoUploader;
import uk.ac.ucl.cs.cmic.giftcloud.uploadapp.GiftCloudDialogs;
import uk.ac.ucl.cs.cmic.giftcloud.uploadapp.ProjectListModel;
import uk.ac.ucl.cs.cmic.giftcloud.uploadapplet.MultiUploadParameters;
import uk.ac.ucl.cs.cmic.giftcloud.uploadapplet.MultiUploadWizard;
import uk.ac.ucl.cs.cmic.giftcloud.util.MultiUploadReporter;

import javax.security.sasl.AuthenticationException;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

public class GiftCloudUploader implements BackgroundUploader.BackgroundUploadOutcomeCallback {
    private final GiftCloudProperties giftCloudProperties;
    private final Container container;
    private final PendingUploadTaskList pendingUploadList;
    private final MultiUploadReporter reporter;
    private final ProjectListModel projectListModel;
    private final GiftCloudServerFactory serverFactory;
    private final BackgroundAddToUploaderService backgroundAddToUploaderService;
    private final GiftCloudAutoUploader autoUploader;
    private final BackgroundUploader backgroundUploader;

    public GiftCloudUploader(final GiftCloudProperties giftCloudProperties, final MultiUploadReporter reporter) {
        this.giftCloudProperties = giftCloudProperties;
        this.container = reporter.getContainer();
        this.reporter = reporter;
        projectListModel = new ProjectListModel(giftCloudProperties);
        serverFactory = new GiftCloudServerFactory(giftCloudProperties, projectListModel, reporter);
        autoUploader = new GiftCloudAutoUploader(serverFactory, reporter);
        pendingUploadList = new PendingUploadTaskList(giftCloudProperties, reporter);
        backgroundAddToUploaderService = new BackgroundAddToUploaderService(pendingUploadList, serverFactory, this, autoUploader, reporter);

        final int numThreads = 1;
        final ProgressHandleWrapper progressHandleWrapper = new ProgressHandleWrapper(reporter);
        backgroundUploader = new BackgroundUploader(new BackgroundCompletionServiceTaskList<Callable<Set<String>>>(numThreads, BackgroundServiceTaskList.BackgroundThreadTermination.CONTINUE_UNTIL_TERMINATED), progressHandleWrapper, this, reporter);
    }

    public void setUploadServiceRunningState(final boolean start) {
        if (start) {
            backgroundAddToUploaderService.start();
        } else {
            backgroundAddToUploaderService.stop();
        }
    }

    /**
     * Launches the interactive wizard for file uploading
     * This method does not throw any exceptions
     *
     * @param multiUploadParameters
     * @return true if the wizard was launched successfully
     */
    public boolean runWizard(final MultiUploadParameters multiUploadParameters) {
        String giftCloudServerUrl = "";

        try {
            final GiftCloudServer giftCloudServer = serverFactory.getGiftCloudServer();
            giftCloudServerUrl = giftCloudServer.getGiftCloudServerUrl();

            final Dimension windowSize = new Dimension(300, 300);
            new MultiUploadWizard(giftCloudServer, giftCloudServer.getRestServerHelper(), windowSize, multiUploadParameters, giftCloudServer.getGiftCloudServerUrl(), reporter);
            return true;

        } catch (CancellationException e) {
            reporter.silentLogException(e, "The upload wizard was cancelled. Server:" + giftCloudServerUrl + ", error:" + e.getMessage());
            // Do not report anything to user, since the user initiated the cancellation
            return false;

        } catch (AuthenticationException e) {
            reporter.silentLogException(e, "The GIFT-Cloud username or password was not recognised. Server:" + giftCloudServerUrl + ", error:" + e.getMessage());
            JOptionPane.showMessageDialog(container, "The GIFT-Cloud username or password was not recognised.", "Error", JOptionPane.DEFAULT_OPTION);
            return false;

        } catch (Exception e) {
            reporter.silentLogException(e, "An error occurred while executing the upload wizard using the GIFT-Cloud server at " + giftCloudServerUrl + ": " + e.getMessage());
            JOptionPane.showMessageDialog(container, "Could not launch the wizard due to the following error: " + e.getMessage(), "Error", JOptionPane.DEFAULT_OPTION);
            return false;
        }
    }

    /**
     * Attempt to connect to the GIFT-Cloud server and initiate a session
     * This method does not throw any exceptions
     *
     * @return true if the authentication was successful
     */
    public boolean tryAuthentication() {
        String giftCloudServerUrl = "";

        // We attempt to connect to the GIFT-Cloud server, in order to authenticate and to set the project list, but we allow the connection to fail gracefully
        try {
            final GiftCloudServer giftCloudServer = serverFactory.getGiftCloudServer();
            giftCloudServerUrl = giftCloudServer.getGiftCloudServerUrl();

            // Allow user to log in again if they have previously cancelled a login dialog
            giftCloudServer.resetCancellation();

            serverFactory.getGiftCloudServer().tryAuthentication();
            return true;

        } catch (CancellationException e) {
            reporter.silentLogException(e, "Authentication was cancelled. Server:" + giftCloudServerUrl + ", error:" + e.getMessage());
            // Do not report anything to user, since the user initiated the cancellation
            return false;

        } catch (AuthenticationException e) {
            reporter.silentLogException(e, "The GIFT-Cloud username or password was not recognised. Server:" + giftCloudServerUrl + ", error:" + e.getMessage());
            JOptionPane.showMessageDialog(container, "The GIFT-Cloud username or password was not recognised.", "Error", JOptionPane.DEFAULT_OPTION);
            return false;

        } catch (Exception e) {
            reporter.silentLogException(e, "An error occurred when attempting to connect to the GIFT-Cloud server at " + giftCloudServerUrl + ": " + e.getMessage());
            JOptionPane.showMessageDialog(container, "Could not connect to the GIFT-Cloud server due to the following error: " + e.getMessage(), "Error", JOptionPane.DEFAULT_OPTION);
            return false;
        }
    }

    public ComboBoxModel<String> getProjectListModel() {
        return projectListModel;
    }

    public boolean uploadToGiftCloud(Vector<String> paths) throws IOException {

        try {
            final GiftCloudServer giftCloudServer = serverFactory.getGiftCloudServer();

            // Allow user to log in again if they have previously cancelled a login dialog
            giftCloudServer.resetCancellation();

            final String projectName = getProjectName(giftCloudServer);

            return autoUploader.uploadToGiftCloud(giftCloudServer, paths, projectName);

        } catch (Throwable throwable) {

            return false;
        }
    }

    public boolean appendToGiftCloud(Vector<String> paths) throws IOException {

        try {
            final GiftCloudServer giftCloudServer = serverFactory.getGiftCloudServer();

            // Allow user to log in again if they have previously cancelled a login dialog
            giftCloudServer.resetCancellation();

            final String projectName = getProjectName(giftCloudServer);

            return autoUploader.appendToGiftCloud(giftCloudServer, paths, projectName);

        } catch (Throwable throwable) {

            return false;
        }
    }

    String getProjectName(final GiftCloudServer giftCloudServer) throws IOException {
        String selectedProjectName = (String) projectListModel.getSelectedItem();
        if (StringUtils.isEmpty(selectedProjectName)) {
            try {
                selectedProjectName = GiftCloudDialogs.showInputDialogToSelectProject(giftCloudServer.getListOfProjects(), container, giftCloudProperties.getLastProject());
            } catch (IOException e) {
                throw new IOException("Unable to retrieve project list due to following error: " + e.getMessage(), e);
            }
        }
        return selectedProjectName;
    }

    public void addFileReference(final String mediaFileName) {
        try {
            final GiftCloudServer giftCloudServer = serverFactory.getGiftCloudServer();

            // Allow user to log in again if they have previously cancelled a login dialog
            giftCloudServer.resetCancellation();

            final String projectName = getProjectName(giftCloudServer);
            pendingUploadList.addFileReference(mediaFileName, Optional.of(projectName));

        } catch (Throwable throwable) {
            // ToDo
        }
    }

    public void addFileInstance(final String dicomFileName) {
        try {
            final GiftCloudServer giftCloudServer = serverFactory.getGiftCloudServer();

            // Allow user to log in again if they have previously cancelled a login dialog
            giftCloudServer.resetCancellation();

            final String projectName = getProjectName(giftCloudServer);
            pendingUploadList.addFileInstance(dicomFileName, Optional.of(projectName));

        } catch (Throwable throwable) {
            // ToDo
        }
    }

    @Override
    public void notifySuccess(final FileCollection fileCollection) {
//        pendingUploadTaskList.notifySuccess(fileCollection);
    }

    @Override
    public void notifyFailure(final FileCollection fileCollection) {
//        pendingUploadTaskList.notifyFailure(fileCollection);
    }

    public void addExistingFilesToUploadQueue() {
        pendingUploadList.addExistingFiles();
    }
}

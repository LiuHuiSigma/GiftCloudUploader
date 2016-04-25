package uk.ac.ucl.cs.cmic.giftcloud.uploader;

import uk.ac.ucl.cs.cmic.giftcloud.data.Study;
import uk.ac.ucl.cs.cmic.giftcloud.dicom.FileCollection;
import uk.ac.ucl.cs.cmic.giftcloud.dicom.MasterTrawler;
import uk.ac.ucl.cs.cmic.giftcloud.restserver.*;
import uk.ac.ucl.cs.cmic.giftcloud.util.EditProgressMonitorWrapper;
import uk.ac.ucl.cs.cmic.giftcloud.util.GiftCloudException;
import uk.ac.ucl.cs.cmic.giftcloud.util.GiftCloudReporter;
import uk.ac.ucl.cs.cmic.giftcloud.util.GiftCloudUploaderError;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class for uploading files and automatically assigning them to projects, subjects, experiments and scans
 */
public class AutoUploader {

    private final BackgroundUploader backgroundUploader;
    private final GiftCloudReporter reporter;
    private final GiftCloudServerFactory serverFactory;
    private final AliasGenerator aliasGenerator;

    /**
     * This class is used to automatically and asynchronously group and upload multiple files to a GIFT-Cloud server
     *
     * @para serverFactory
     * @param serverFactory
     * @param reporter
     */
    public AutoUploader(GiftCloudServerFactory serverFactory, final BackgroundUploader backgroundUploader, final GiftCloudProperties properties, final GiftCloudReporter reporter) {
        this.serverFactory = serverFactory;
        this.backgroundUploader = backgroundUploader;
        this.reporter = reporter;
        aliasGenerator = new AliasGenerator(properties, reporter);
    }

    /**
     * Upload a set of files to the server
     *
     * @param paths the set of files to upload
     * @param projectName the project to which the files will be added
     * @param append whether to create a new upload or append files to an existing dataset
     * @throws IOException
     */
    boolean uploadToGiftCloud(final List<String> paths, final String projectName, final boolean append) throws IOException {
        final GiftCloudServer server = serverFactory.getGiftCloudServer();

        final List<File> fileList = new ArrayList<File>();
        for (final String path : paths) {
            fileList.add(new File(path));
        }

        final Project project = server.getProject(projectName);
        final SeriesImportFilterApplicatorRetriever seriesImportFilter = project.getSeriesImportFilter(server);

        final EditProgressMonitorWrapper progressWrapper = new EditProgressMonitorWrapper(reporter);
        final MasterTrawler trawler = new MasterTrawler(progressWrapper, fileList, seriesImportFilter);
        final List<Study> studies = trawler.call();

        for (final Study study : studies) {
            addSessionToUploadList(server, project, projectName, study, append);
        }

        List<GiftCloudUploaderError> errors = trawler.getErrorMessages();

        // If any files failed to upload, we log all of them and throw an exception for the first one
        if (errors.size() > 0) {
            final Set<String> uniqueErrors = new HashSet<String>();
            for (final GiftCloudUploaderError error : errors) {
                uniqueErrors.add(error.getUserVisibleMessage());
            }
            final String prefixMessage = errors.size() == 1 ? "1 error" : String.valueOf(errors.size()) + " errors";
            final StringBuilder builder = new StringBuilder();
            builder.append("<html>" + prefixMessage + " occurred during upload:");
            for (final String errorText : uniqueErrors) {
                builder.append("<br>" + errorText);
            }
            builder.append("</html");

            // We would ideally like to display a message to the user if uploading has been initiated via importing, but not in a background context. Not possible with current architecture so suppress the dialog and let the status bar show the error
//            reporter.showMessageToUser(builder.toString());
            throw new GiftCloudException(errors.get(0));
        }

        return true;
    }

    /**
     * Force saving of the patient list
     */
    public void exportPatientList() {
        aliasGenerator.exportPatientList();
    }

    private void addSessionToUploadList(final GiftCloudServer server, final Project project, final String projectName, final Study study, final boolean append) throws IOException {
        final String patientId = study.getPatientId();
        final String patientName = study.getPatientName();
        final String studyInstanceUid = study.getStudyUid();
        final String seriesUid = study.getSeriesUid();

        final XnatModalityParams xnatModalityParams = study.getXnatModalityParams();

        final GiftCloudLabel.SubjectLabel subjectLabel = aliasGenerator.getSubjectName(server, projectName, patientId, patientName);
        final GiftCloudLabel.ExperimentLabel experimentLabel = aliasGenerator.getSessionName(server, projectName, subjectLabel, studyInstanceUid, xnatModalityParams);
        final GiftCloudLabel.ScanLabel scanName = aliasGenerator.getScanName(server, projectName, subjectLabel, experimentLabel, seriesUid, xnatModalityParams);

        final List<FileCollection> fileCollections = study.getFiles();

        if (fileCollections.isEmpty()) {
            throw new IOException("No files were selected for upload");
        }

        // Iterate through each set of files
        for (final FileCollection fileCollection : fileCollections) {
            final UploadParameters uploadParameters = new UploadParameters();
            uploadParameters.setProjectName(projectName);
            uploadParameters.setSubjectLabel(subjectLabel);
            uploadParameters.setExperimentLabel(experimentLabel);
            uploadParameters.setScanLabel(scanName);
            uploadParameters.setFileCollection(fileCollection);
            uploadParameters.setXnatModalityParams(xnatModalityParams);

            final ZipSeriesUploader uploader = new ZipSeriesUploader(uploadParameters, server, study.getSeriesZipper(project, uploadParameters), append);
            backgroundUploader.addUploader(uploader);
        }
    }

    public void tryAuthentication() throws IOException {
        serverFactory.getGiftCloudServer().tryAuthentication();
    }
}

package uk.ac.ucl.cs.cmic.giftcloud.workers;

import uk.ac.ucl.cs.cmic.giftcloud.restserver.GiftCloudHttpException;
import uk.ac.ucl.cs.cmic.giftcloud.uploadapp.FileUploadSuccessCallback;
import uk.ac.ucl.cs.cmic.giftcloud.uploadapp.GiftCloudReporterFromApplication;
import uk.ac.ucl.cs.cmic.giftcloud.uploader.GiftCloudUploader;

import java.util.Vector;

public class GiftCloudAppendUploadWorker implements Runnable {
    private final Vector<String> sourceFilePathSelections;
    private GiftCloudUploader giftCloudUploader;
    private FileUploadSuccessCallback uploadSuccessCallback;
    private GiftCloudReporterFromApplication reporter;

    public GiftCloudAppendUploadWorker(Vector<String> sourceFilePathSelections, final GiftCloudUploader giftCloudUploader, final FileUploadSuccessCallback uploadSuccessCallback, final GiftCloudReporterFromApplication reporter) {
        this.sourceFilePathSelections = sourceFilePathSelections;
        this.giftCloudUploader = giftCloudUploader;
        this.uploadSuccessCallback = uploadSuccessCallback;
        this.reporter = reporter;
    }

    public void run() {
        if (giftCloudUploader == null) {
            reporter.showError("An error occurred which prevents the uploader from connecting to the server. Please restart GIFT-Cloud uploader.");
            return;
        }

        reporter.setWaitCursor();

        if (sourceFilePathSelections == null) {
            reporter.updateStatusText("GIFT-Cloud upload: no files were selected for upload");
            reporter.showError("No files were selected for uploading.");
        } else {
            reporter.sendLn("GIFT-Cloud upload started");
            reporter.startProgressBar();

            for (final String fileName : sourceFilePathSelections) {
                try {
                    Vector<String> singleFile = new Vector<String>();
                    singleFile.add(fileName);

                    System.out.println("Uploading single file: " + fileName);
                    if (giftCloudUploader.appendToGiftCloud(singleFile)) {
                        reporter.updateStatusText("GIFT-Cloud upload complete");
                    } else {
                        reporter.updateStatusText("Partial failure in GIFT-Cloud upload");
                        uploadSuccessCallback.addFailedUpload(fileName);
                    }
                } catch (GiftCloudHttpException e) {
                    reporter.updateStatusText("Partial failure in GIFT-Cloud upload, due to the following error: " + e.getHtmlText());
                    e.printStackTrace(System.err);
                } catch (Exception e) {
                    reporter.updateStatusText("Failure in GIFT-Cloud upload, due to the following error: " + e.toString());
                    e.printStackTrace(System.err);
                }
            }

            reporter.endProgressBar();
        }
        reporter.restoreCursor();
    }
}

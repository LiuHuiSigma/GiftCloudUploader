package com.pixelmed.display;

import com.pixelmed.dicom.*;
import com.pixelmed.utils.CapabilitiesAvailable;
import com.pixelmed.utils.FileUtilities;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.Vector;

public class BlackoutCurrentImage {

    private AttributeList list;
    private SourceImage sImg;
    private boolean changesWereMade;
    private boolean usedjpegblockredaction;
    private File redactedJPEGFile;
    private BlackoutImage blackoutImage;

    public BlackoutCurrentImage() {
    }

    public SourceImage getSourceImage() {
        return sImg;
    }

    public void save(String currentFileName, boolean burnInOverlays, String ourAETitle, int burnedinflag) throws IOException, DicomException {
        boolean success = true;
        try {
            sImg.close();        // in case memory-mapped pixel data open; would inhibit Windows rename or copy/reopen otherwise
            sImg = null;
            System.gc();                    // cannot guarantee that buffers will be released, causing problems on Windows, but try ... http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4715154 :(
            System.runFinalization();
            System.gc();
        } catch (Throwable t) {
            // Save failed - unable to close image - not saving modifications
            success = false;
        }
        File currentFile = new File(currentFileName);
        File newFile = new File(currentFileName + ".new");
        if (success) {
            String transferSyntaxUID = Attribute.getSingleStringValueOrEmptyString(list, TagFromName.TransferSyntaxUID);
            try {
                String outputTransferSyntaxUID = null;
                if (usedjpegblockredaction && redactedJPEGFile != null) {
                    // do not repeat the redaction, reuse redactedJPEGFile, without decompressing the pixels, so that we can update the technique stuff in the list
                    DicomInputStream i = new DicomInputStream(redactedJPEGFile);
                    list = new AttributeList();
                    list.setDecompressPixelData(false);
                    list.read(i);
                    i.close();
                    outputTransferSyntaxUID = TransferSyntax.JPEGBaseline;
                } else {
                    outputTransferSyntaxUID = TransferSyntax.ExplicitVRLittleEndian;
                    list.correctDecompressedImagePixelModule();
                    list.insertLossyImageCompressionHistoryIfDecompressed();
                }
                if (burnedinflag != BurnedInAnnotationFlagAction.LEAVE_ALONE) {
                    list.remove(TagFromName.BurnedInAnnotation);
                    if (burnedinflag == BurnedInAnnotationFlagAction.ADD_AS_NO_IF_SAVED
                            || (burnedinflag == BurnedInAnnotationFlagAction.ADD_AS_NO_IF_CHANGED && changesWereMade)) {
                        Attribute a = new CodeStringAttribute(TagFromName.BurnedInAnnotation);
                        a.addValue("NO");
                        list.put(a);
                    }
                }
                if (changesWereMade) {
                    addDeidentificationMethod(burnInOverlays, list);
                }
                list.removeGroupLengthAttributes();
                list.removeMetaInformationHeaderAttributes();
                list.remove(TagFromName.DataSetTrailingPadding);

                FileMetaInformation.addFileMetaInformation(list, outputTransferSyntaxUID, ourAETitle);
                list.write(newFile, outputTransferSyntaxUID, true/*useMeta*/, true/*useBufferedStream*/);

                list = null;
                try {
                    currentFile.delete();
                    FileUtilities.renameElseCopyTo(newFile, currentFile);
                } catch (IOException e) {
                    // Unable to rename or copy - save failed - not saving modifications
                    success = false;
                }

                if (redactedJPEGFile != null) {
                    redactedJPEGFile.delete();
                    redactedJPEGFile = null;
                }
                usedjpegblockredaction = false;

                changesWereMade = false;
                // "Save of "+currentFileName+" succeeded"
            } catch (DicomException e) {
                // Save failed
            } catch (IOException e) {
                // Save failed
            }
        }
        loadDicomFileOrDirectory(currentFile);
    }

    public void loadDicomFileOrDirectory(String currentFileName) throws IOException, DicomException {
        File currentFile = FileUtilities.getFileFromNameInsensitiveToCaseIfNecessary(currentFileName);
        loadDicomFileOrDirectory(currentFile);
    }

    /**
     * <p>Load the named DICOM file and display it in the image panel.</p>
     *
     * @param    currentFile
     */
    protected void loadDicomFileOrDirectory(File currentFile) throws IOException, DicomException {
        blackoutImage = new BlackoutImage(currentFile);
        changesWereMade = false;
        list = readAttributeList(currentFile, true);
        String useSOPClassUID = Attribute.getSingleStringValueOrEmptyString(list, TagFromName.SOPClassUID);
        if (SOPClass.isImageStorage(useSOPClassUID)) {
            sImg = new SourceImage(list);
        } else {
            throw new DicomException("unsupported SOP Class " + useSOPClassUID);
        }
    }


    public void apply(Vector<Rectangle2D.Double> shapes, boolean burnInOverlays, boolean usePixelPaddingBlackoutValue, boolean useZeroBlackoutValue) throws Exception {
        if (sImg != null && list != null) {
            if ((shapes != null && shapes.size() > 0) || burnInOverlays) {
                changesWereMade = true;
                String transferSyntaxUID = Attribute.getSingleStringValueOrEmptyString(list, TagFromName.TransferSyntaxUID);
                if (transferSyntaxUID.equals(TransferSyntax.JPEGBaseline) && !burnInOverlays && CapabilitiesAvailable.haveJPEGBaselineSelectiveBlockRedaction()) {
                    usedjpegblockredaction = true;
                    if (redactedJPEGFile != null) {
                        redactedJPEGFile.delete();
                    }
                    redactedJPEGFile = File.createTempFile("DicomImageBlackout", ".dcm");
                    ImageEditUtilities.blackoutJPEGBlocks(new File(blackoutImage.getCurrentFileName()), redactedJPEGFile, shapes);
                    // Need to re-read the file because we need to decompress the redacted JPEG to use to display it again
                    list = readAttributeList(redactedJPEGFile, true);
                    // do NOT delete redactedJPEGFile, since will reuse it when "saving", and also file may need to hang around for display of cached pixel data
                } else {
                    usedjpegblockredaction = false;
                    ImageEditUtilities.blackout(sImg, list, shapes, burnInOverlays, usePixelPaddingBlackoutValue, useZeroBlackoutValue, 0);
                }
                sImg = new SourceImage(list);    // remake SourceImage, in case blackout() changed the AttributeList (e.g., removed overlays)
            } else {
            }
        }

    }

    public boolean UnsavedChanges() {
        return changesWereMade;
    }

    public int getNumberOfImages() {
        return Attribute.getSingleIntegerValueOrDefault(list, TagFromName.NumberOfFrames, 1);
    }

    private static AttributeList readAttributeList(File currentFile, boolean decompressPixelData) throws IOException, DicomException {
        DicomInputStream i = new DicomInputStream(currentFile);
        AttributeList attributeList = new AttributeList();
        if (!decompressPixelData) {
            attributeList.setDecompressPixelData(decompressPixelData);
        }
        attributeList.read(i);
        i.close();
        return attributeList;
    }

    public static void loadAndApplyAndSave(File inputFile, File outputFile, Vector<Rectangle2D.Double> shapes, boolean burnInOverlays, boolean usePixelPaddingBlackoutValue, boolean useZeroBlackoutValue, String ourAETitle) throws IOException, DicomException {
        AttributeList attributeList = readAttributeList(inputFile, true);
        if (attributeList == null) {
            throw new DicomException("Could not read image");
        }

        String useSOPClassUID = Attribute.getSingleStringValueOrEmptyString(attributeList, TagFromName.SOPClassUID);
        if (!SOPClass.isImageStorage(useSOPClassUID)) {
            throw new DicomException("unsupported SOP Class " + useSOPClassUID);
        }

        String outputTransferSyntaxUID = null;
        if ((shapes != null && shapes.size() > 0) || burnInOverlays) {
            String transferSyntaxUID = Attribute.getSingleStringValueOrEmptyString(attributeList, TagFromName.TransferSyntaxUID);

            if (transferSyntaxUID.equals(TransferSyntax.JPEGBaseline) && !burnInOverlays && CapabilitiesAvailable.haveJPEGBaselineSelectiveBlockRedaction()) {
                // For a JPEG file we black out the image blocks

                outputTransferSyntaxUID = TransferSyntax.JPEGBaseline;

                // Perform a blackout of the JPEG blocks - this writes out to a temporary file
                File redactedJPEGFile = File.createTempFile("BlackoutJpegFile", ".dcm");
                try {
                    ImageEditUtilities.blackoutJPEGBlocks(inputFile, redactedJPEGFile, shapes);
                } catch (Exception e) {
                    throw new DicomException("JPEG blackout failed: " + e.getLocalizedMessage());
                }

                // Now read in the new attributes in from the temporary file
                attributeList = readAttributeList(redactedJPEGFile, true);

            } else {
                // For other files we black out the image data

                outputTransferSyntaxUID = TransferSyntax.ExplicitVRLittleEndian;

                SourceImage sImg = new SourceImage(attributeList);
                if (sImg == null) {
                    throw new DicomException("Could not read image");
                }

                ImageEditUtilities.blackout(sImg, attributeList, shapes, burnInOverlays, usePixelPaddingBlackoutValue, useZeroBlackoutValue, 0);
                try {
                    sImg.close();
                } catch (Throwable throwable) {
                }

                attributeList.correctDecompressedImagePixelModule();
                attributeList.insertLossyImageCompressionHistoryIfDecompressed();
            }
        }

        addDeidentificationMethod(burnInOverlays, attributeList);

        // Set BurnedInAnnotation attribute to NO
        attributeList.remove(TagFromName.BurnedInAnnotation);
        Attribute a = new CodeStringAttribute(TagFromName.BurnedInAnnotation);
        a.addValue("NO");
        attributeList.put(a);

        // Update header attributes
        attributeList.removeGroupLengthAttributes();
        attributeList.removeMetaInformationHeaderAttributes();
        attributeList.remove(TagFromName.DataSetTrailingPadding);
        FileMetaInformation.addFileMetaInformation(attributeList, outputTransferSyntaxUID, ourAETitle);

        // Write out the new file
        attributeList.write(outputFile, outputTransferSyntaxUID, true/*useMeta*/, true/*useBufferedStream*/);
    }

    private static void addDeidentificationMethod(boolean burnInOverlays, AttributeList list) throws DicomException {
        {
            Attribute aDeidentificationMethod = list.get(TagFromName.DeidentificationMethod);
            if (aDeidentificationMethod == null) {
                aDeidentificationMethod = new LongStringAttribute(TagFromName.DeidentificationMethod);
                list.put(aDeidentificationMethod);
            }
            if (burnInOverlays) {
                aDeidentificationMethod.addValue("Overlays burned in then blacked out");
            }
            aDeidentificationMethod.addValue("Burned in text blacked out");
        }
        {
            SequenceAttribute aDeidentificationMethodCodeSequence = (SequenceAttribute) (list.get(TagFromName.DeidentificationMethodCodeSequence));
            if (aDeidentificationMethodCodeSequence == null) {
                aDeidentificationMethodCodeSequence = new SequenceAttribute(TagFromName.DeidentificationMethodCodeSequence);
                list.put(aDeidentificationMethodCodeSequence);
            }
            aDeidentificationMethodCodeSequence.addItem(new CodedSequenceItem("113101", "DCM", "Clean Pixel Data Option").getAttributeList());
        }
    }

    private void forceCloseImage(SourceImage sImg) throws DicomException {
        try {
            this.sImg.close();        // in case memory-mapped pixel data open; would inhibit Windows rename or copy/reopen otherwise
            this.sImg = null;
            System.gc();                    // cannot guarantee that buffers will be released, causing problems on Windows, but try ... http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4715154 :(
            System.runFinalization();
            System.gc();
        } catch (Throwable t) {
            // Save failed - unable to close image - not saving modifications
            throw new DicomException("Failed to close image");
        }
    }
}

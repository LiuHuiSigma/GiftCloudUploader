/*
 * uk.ac.ucl.cs.cmic.giftcloud.dicom.Study
 * XNAT http://www.xnat.org
 * Copyright (c) 2014, Washington University School of Medicine
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 *
 * Last modified 2/11/14 4:28 PM
 */
package uk.ac.ucl.cs.cmic.giftcloud.dicom;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import netscape.javascript.JSObject;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.netbeans.spi.wizard.ResultProgressHandle;
import org.nrg.dcm.edit.DicomUtils;
import org.nrg.dcm.edit.ScriptApplicator;
import org.nrg.dcm.edit.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ucl.cs.cmic.giftcloud.data.Project;
import uk.ac.ucl.cs.cmic.giftcloud.data.Session;
import uk.ac.ucl.cs.cmic.giftcloud.data.SessionVariable;
import uk.ac.ucl.cs.cmic.giftcloud.data.UploadFailureHandler;
import uk.ac.ucl.cs.cmic.giftcloud.uploader.GiftCloudServer;
import uk.ac.ucl.cs.cmic.giftcloud.util.MultiUploadReporter;
import uk.ac.ucl.cs.cmic.giftcloud.restserver.RestServerHelper;
import uk.ac.ucl.cs.cmic.giftcloud.restserver.SessionParameters;
import uk.ac.ucl.cs.cmic.giftcloud.restserver.XnatModalityParams;
import uk.ac.ucl.cs.cmic.giftcloud.util.MapRegistry;
import uk.ac.ucl.cs.cmic.giftcloud.util.Registry;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Study extends MapEntity implements Entity, Session {

    public static final int MAX_TAG = Collections.max(new ArrayList<Integer>() {{
        add(Tag.AccessionNumber);
        add(Tag.StudyDate);
        add(Tag.StudyDescription);
        add(Tag.StudyID);
        add(Tag.StudyInstanceUID);
        add(Tag.StudyTime);
    }});

    private final Logger logger = LoggerFactory.getLogger(Study.class);
    private final Registry<Series> series = new MapRegistry<Series>(new TreeMap<Series, Series>());
    private final Date dateTime;
    private final String patientId; // This is not stored
    private final String studyUid; // This is not stored
    private final String seriesUid; // This is not stored

    private Study(final String uid, final Date dateTime, final String id, final String accessionNumber, final String description, final String patientId, final String seriesInstanceUid, final String studyInstanceUid) {
        put(Tag.StudyInstanceUID, uid);
        this.dateTime = dateTime;
        if (null != dateTime) {
            put(Tag.StudyDate, new SimpleDateFormat("yyyyMMdd").format(dateTime));
            put(Tag.StudyTime, new SimpleDateFormat("HHmmss").format(dateTime));
        }
        put(Tag.StudyID, id);
        put(Tag.AccessionNumber, accessionNumber);
        put(Tag.StudyDescription, description);
        this.patientId = patientId;
        this.seriesUid = seriesInstanceUid;
        this.studyUid = studyInstanceUid;
    }

    public Study(final DicomObject o) {
        this(o.getString(Tag.StudyInstanceUID),
                DicomUtils.getDateTime(o, Tag.StudyDate, Tag.StudyTime),
                o.getString(Tag.StudyID),
                o.getString(Tag.AccessionNumber),
                o.getString(Tag.StudyDescription),
                o.getString(Tag.PatientID),
                o.getString(Tag.SeriesInstanceUID),
                o.getString(Tag.StudyInstanceUID));
    }

    @Override
    public String getPatientId() {
        return patientId;
    }


    @Override
    public String getStudyUid() {
        return studyUid;
    }

    @Override
    public String getSeriesUid() {
        return seriesUid;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object o) {
        return o instanceof Study && Objects.equal(get(Tag.StudyInstanceUID), ((Study) o).get(Tag.StudyInstanceUID));
    }

    /*
     * (non-Javadoc)
     * @see Session#getAccession()
     */
    public String getAccession() {
        return (String) get(Tag.AccessionNumber);
    }

    /*
     * (non-Javadoc)
     * @see Session#getDateTime()
     */
    public Date getDateTime() {
        return dateTime;
    }

    /*
     * (non-Javadoc)
     * @see Session#getTimeZone()
     */
    public TimeZone getTimeZone() {
        // DICOM does not store timezone information so return null
        return null;
    }

    /*
     * (non-Javadoc)
     * @see Session#getDescription()
     */
    public String getDescription() {
        return (String) get(Tag.StudyDescription);
    }

    /*
     * (non-Javadoc)
     * @see Session#getFileCount()
     */
    public int getFileCount() {
        return getFileCount(series);
    }

    private static int getFileCount(final Registry<Series> series) {
        int count = 0;
        for (final Series s : series) {
            count += s.getFileCount();
        }
        return count;
    }

    /*
     * (non-Javadoc)
     * @see Session#getFormat()
     */
    public String getFormat() {
        return "DICOM";
    }

    /*
     * (non-Javadoc)
     * @see Session#getID()
     */
    public String getID() {
        return (String) get(Tag.StudyID);
    }

    /*
     * (non-Javadoc)
     * @see org.nrg.dcm.MapEntity#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(get(Tag.StudyInstanceUID));
    }

    /*
     * (non-Javadoc)
     * @see Session#getModalities()
     */
    public Set<String> getModalities() {
        final Set<String> modalities = Sets.newLinkedHashSet();
        for (final Series s : series) {
            modalities.addAll(s.getModalities());
        }
        return modalities;
    }

    /*
     * (non-Javadoc)
     * @see Session#getScanCount()
     */
    public int getScanCount() {
        return series.size();
    }

    public Series getSeries(final DicomObject o, final File f) {
        final Series s = series.get(new Series(this, o));
        s.addFile(f, o);
        return s;
    }

    /*
     * (non-Javadoc)
     * @see uk.ac.ucl.cs.cmic.giftcloud.dicom.Entity#getSeries()
     */
    public Collection<Series> getSeries() {
        return series.getAll();
    }

    /*
     * (non-Javadoc)
     * @see Session#getSize()
     */
    public long getSize() {
        long size = 0;
        for (final Series s : series) {
            size += s.getSize();
        }
        return size;
    }

    /*
     * (non-Javadoc)
     * @see uk.ac.ucl.cs.cmic.giftcloud.dicom.Entity#getStudies()
     */
    public Collection<Study> getStudies() {
        return Collections.singleton(this);
    }

    /**
     * Provides a study identifier that is as unique and verbose as possible.
     *
     * @return The study identifier.
     * @see java.lang.Object#toString()
     */
    public String toString() {
        final StringBuilder builder = new StringBuilder("DICOM study ");
        final Object studyId = get(Tag.StudyID);
        builder.append(studyId);
        final Object accessionNumber = get(Tag.AccessionNumber);
        if (null != accessionNumber) {
            builder.append(" (").append(accessionNumber).append(")");
        }
        final Object description = get(Tag.StudyDescription);
        if (null != description) {
            builder.append(" ").append(description);
        }
        if (null == studyId && null == accessionNumber) {
            builder.append(" [").append(get(Tag.StudyInstanceUID)).append("]");
        }
        return builder.toString();
    }

    /*
     * (non-Javadoc)
     * @see Session#uploadTo(java.util.Map, UploadFailureHandler, org.netbeans.spi.wizard.ResultProgressHandle)
     */
    public boolean uploadTo(final String projectLabel, final String subjectLabel, final GiftCloudServer server, final SessionParameters sessionParameters, Project project, final ResultProgressHandle progress, final Optional<String> windowName, final Optional<JSObject> jsContext, final UploadFailureHandler failureHandler, final MultiUploadReporter logger) throws IOException {

        final List<FileCollection> fileCollections = getFiles();

        if (fileCollections.isEmpty()) {
            progress.failed("No files were selected for upload", true);
            return false;
        }

        final XnatModalityParams xnatModalityParams = getXnatModalityParams();

        final Iterable<ScriptApplicator> applicators = project.getDicomScriptApplicators();
        return server.uploadToStudy(fileCollections, xnatModalityParams, applicators, projectLabel, subjectLabel, sessionParameters, progress, windowName, jsContext, logger);
    }

    /*
         * (non-Javadoc)
         * @see Session#uploadTo(java.util.Map, UploadFailureHandler, org.netbeans.spi.wizard.ResultProgressHandle)
         */
    public boolean appendTo(final String projectLabel, final String subjectLabel, final GiftCloudServer server, final SessionParameters sessionParameters, Project project, final ResultProgressHandle progress, final Optional<String> windowName, final Optional<JSObject> jsContext, final UploadFailureHandler failureHandler, final MultiUploadReporter logger) throws IOException {

        final List<FileCollection> fileCollections = getFiles();

        if (fileCollections.isEmpty()) {
            progress.failed("No files were selected for upload", true);
            return false;
        }

        final XnatModalityParams xnatModalityParams = getXnatModalityParams();

        final Iterable<ScriptApplicator> applicators = project.getDicomScriptApplicators();
        return server.appendToStudy(fileCollections, xnatModalityParams, applicators, projectLabel, subjectLabel, sessionParameters, progress, windowName, jsContext, logger);
    }

    public List<FileCollection> getFiles() {
        final List<Series> uploads = Lists.newArrayList(Iterables.filter(series, new Predicate<Series>() {
            public boolean apply(final Series s) {
                return s.isUploadAllowed();
            }
        }));

        final List<FileCollection> fileCollections = new ArrayList<FileCollection>();
        for (final Series series : uploads) {
            fileCollections.add(series);
        }
        return fileCollections;
    }


    /*
     * (non-Javadoc)
     * @see Session#getVariables()
     */
    public List<SessionVariable> getVariables(final Project project, final Session session) {
        final LinkedHashSet<Variable> dvs = Sets.newLinkedHashSet();
        try {
            // This replaces variables in later scripts with similarly-name variables from
            // earlier scripts. Therefore scripts whose variables should take precedence
            // must appear earlier in the list.
            final Iterable<ScriptApplicator> applicators = project.getDicomScriptApplicators();
            for (final ScriptApplicator a : applicators) {
                for (final Variable v : dvs) {
                    a.unify(v);
                }
                dvs.addAll(a.getVariables().values());
            }
        } catch (Throwable t) { // ToDo: remove this catch, because we want the operation to fail if there is no script
            logger.warn("unable to load script", t);
            return Collections.emptyList();
        }
        final DicomObject o = series.isEmpty() ? null : series.get(0).getSampleObject();
        final List<SessionVariable> vs = Lists.newArrayList();
        for (final Variable dv : dvs) {
            vs.add(DicomSessionVariable.getSessionVariable(dv, o));
        }
        return vs;
    }

    public XnatModalityParams getXnatModalityParams() {
        final Set<XnatModalityParams> xnatModalityParams = Sets.newLinkedHashSet();
        for (final Series s : series) {
            xnatModalityParams.add(s.getModalityParams());
        }

        // ToDo: we are only returning one modality param
        return xnatModalityParams.iterator().next();
    }
}

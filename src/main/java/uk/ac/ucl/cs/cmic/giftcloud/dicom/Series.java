/*
 * uk.ac.ucl.cs.cmic.giftcloud.dicom.Series
 * XNAT http://www.xnat.org
 * Copyright (c) 2014, Washington University School of Medicine
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 *
 * Last modified 2/11/14 4:28 PM
 */
package uk.ac.ucl.cs.cmic.giftcloud.dicom;

import com.google.common.collect.Sets;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import uk.ac.ucl.cs.cmic.giftcloud.restserver.XnatModalityParams;

import java.io.File;
import java.util.*;


public class Series extends MapEntity implements Entity,Comparable<Series>,Iterable<File> {

    public static final int MAX_TAG = Collections.max(new ArrayList<Integer>() {{
        add(Tag.SOPClassUID);
        add(Tag.SeriesNumber);
        add(Tag.SeriesInstanceUID);
        add(Tag.SeriesDescription);
        add(Tag.Modality);
    }});

    private final Set<File> files = Sets.newLinkedHashSet();
    private boolean uploadAllowed = true;
    private XnatModalityParams modalityParams;

    Series(final String uid, final int number, final String modality, final String description, final String sopClassUid) {
        put(Tag.SeriesInstanceUID, uid);
        put(Tag.SeriesNumber, number);
        put(Tag.SeriesDescription, description);
        modalityParams = XnatModalityParams.createFromDicom(modality, sopClassUid);
    }

    Series(final DicomObject o) {
        this(
                o.getString(Tag.SeriesInstanceUID),
                o.getInt(Tag.SeriesNumber),
                o.getString(Tag.Modality),
                o.getString(Tag.SeriesDescription),
                o.getString(Tag.SOPClassUID));
    }

    public void addFile(final File f, final DicomObject o) {
        files.add(f);
    }

    
    private int compareObject(Comparable a, Comparable b) {
    	if (a == null && b == null){
    		return 0;
    	} else if (a == null) {
    		return -1;
    	} else if (b == null) {
    		return 1;
    	} else {
    		return a.compareTo(b);
    	}
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(final Series o) {
        final Integer n = (Integer)get(Tag.SeriesNumber);
        final Integer on = (Integer)o.get(Tag.SeriesNumber);
        
        // if seriesinstanceUID's are the same, 
        //   then do comparison on series numbers.
        // else use the series instance comparison.
          
        if(0 == compareObject(((String)this.get(Tag.SeriesInstanceUID)), ((String)o.get(Tag.SeriesInstanceUID)))){
        	return compareObject(n,on);
        } else {
        	return compareObject(((String)this.get(Tag.SeriesInstanceUID)), ((String)o.get(Tag.SeriesInstanceUID)));
        }        	
    }

    public int getFileCount() {
        return files.size();
    }

    public Collection<File> getFiles() {
        return Collections.unmodifiableCollection(files);
    }

    public String getDescription() {
        final Object n = get(Tag.SeriesDescription);
        return null == n ? null : (String) n;
    }

    final String getNumber() {
        final Object n = this.get(Tag.SeriesNumber);
        return null == n ? null : n.toString();
    }

    final String getUID() {
        return (String)this.get(Tag.SeriesInstanceUID);
    }

    /*
     * (non-Javadoc)
     * @see uk.ac.ucl.cs.cmic.giftcloud.dicom.Entity#getSeries()
     */
    public Collection<Series> getSeries() {
        return Collections.singleton(this);
    }

    public long getSize() {
        long size = 0;
        for (final File f : files) {
            size += f.length();
        }
        return size;
    }

    public Iterator<File> iterator() {
        return Collections.unmodifiableSet(files).iterator();
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object o) {
        return o instanceof Series && getAttributes().equals(((Entity)o).getAttributes());
    }

    /*
     * (non-Javadoc)
     * @see uk.ac.ucl.cs.cmic.giftcloud.dicom.MapEntity#hashCode()
     */
    @Override
    public int hashCode() {
        return getAttributes().hashCode();
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder("Series ");
        sb.append(getNumber());
        return sb.toString();
    }

    public boolean isUploadAllowed() {
        return uploadAllowed;
    }

    public XnatModalityParams getModalityParams() {
        return modalityParams;
    }

    /**
     * @return a minimally-sized object containing the FileCollection
     */
    public FileCollection getMinimalFileCollection() {
        return new SeriesFileCollection(this);
    }
}

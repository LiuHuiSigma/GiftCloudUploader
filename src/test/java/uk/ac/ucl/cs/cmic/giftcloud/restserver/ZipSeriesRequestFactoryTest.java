package uk.ac.ucl.cs.cmic.giftcloud.restserver;

import junit.framework.Assert;
import org.junit.Test;
import org.nrg.dcm.edit.ScriptApplicator;
import uk.ac.ucl.cs.cmic.giftcloud.dicom.FileCollection;
import uk.ac.ucl.cs.cmic.giftcloud.util.MultiUploadReporter;

import java.util.ArrayList;

import static org.mockito.Mockito.mock;

public class ZipSeriesRequestFactoryTest {

    @Test
    public void testBuild() throws Exception {
        final String url = "testUrl";
        final FileCollection fileCollection = mock(FileCollection.class);
        final MultiUploadReporter reporter = mock(MultiUploadReporter.class);
        final GiftCloudProperties giftCloudProperties = mock(GiftCloudProperties.class);
        final Iterable<ScriptApplicator> applicators = new ArrayList<ScriptApplicator>();
        final UploadStatisticsReporter progress = mock(UploadStatisticsReporter.class);
        {
            final HttpRequestWithOutput requestFixedSize = ZipSeriesRequestFactory.build(HttpConnectionWrapper.ConnectionType.POST, ZipSeriesRequestFactory.ZipStreaming.FixedSize, url, fileCollection, applicators, progress, new HttpEmptyResponseProcessor(), giftCloudProperties, reporter);
            Assert.assertTrue(requestFixedSize instanceof ZipSeriesRequestFixedSize);
        }

        {
            final HttpRequestWithOutput requestChunked = ZipSeriesRequestFactory.build(HttpConnectionWrapper.ConnectionType.POST, ZipSeriesRequestFactory.ZipStreaming.Chunked, url, fileCollection, applicators, progress, new HttpEmptyResponseProcessor(), giftCloudProperties, reporter);
            Assert.assertTrue(requestChunked instanceof ZipSeriesRequestChunked);
        }
    }
}
/*=============================================================================

  GIFT-Cloud: A data storage and collaboration platform

  Copyright (c) University College London (UCL). All rights reserved.

  Parts of this software are derived from XNAT
    http://www.xnat.org
    Copyright (c) 2014, Washington University School of Medicine
    All Rights Reserved
    Released under the Simplified BSD.

  This software is distributed WITHOUT ANY WARRANTY; without even
  the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
  PURPOSE.

  See LICENSE.txt in the top level directory for details.

=============================================================================*/

package uk.ac.ucl.cs.cmic.giftcloud.restserver;

import org.json.JSONException;
import org.nrg.dcm.edit.ScriptApplicator;
import uk.ac.ucl.cs.cmic.giftcloud.dicom.FileCollection;
import uk.ac.ucl.cs.cmic.giftcloud.util.GiftCloudReporter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class RestServerSessionHelper {

    private final GiftCloudSession giftCloudSession;
    private GiftCloudProperties giftCloudProperties;
    private GiftCloudReporter reporter;

    public RestServerSessionHelper(final String baseUrlString, final GiftCloudProperties giftCloudProperties, final ConnectionFactory connectionFactory, final GiftCloudReporter reporter) throws MalformedURLException {
        this.giftCloudProperties = giftCloudProperties;
        this.reporter = reporter;

        giftCloudSession = new GiftCloudSession(baseUrlString, giftCloudProperties, connectionFactory, reporter);
    }

    public void tryAuthentication() throws IOException {
        giftCloudSession.tryAuthentication();
    }

    public Collection<Object> getValues(final String path, final String key) throws IOException, JSONException {
        return giftCloudSession.request(new HttpRequestWithoutOutput<Collection<Object>>(HttpConnection.ConnectionType.GET, path, new HttpJsonResponseProcessor(new JSONValuesExtractor(key)), giftCloudProperties, reporter));
    }

    public Map<String, String> getAliases(final String path, final String aliasKey, final String idKey) throws IOException, JSONException {
        return giftCloudSession.request(new HttpRequestWithoutOutput<Map<String, String>>(HttpConnection.ConnectionType.GET, path, new HttpJsonResponseProcessor(new JSONAliasesExtractor(aliasKey, idKey)), giftCloudProperties, reporter));
    }

    public Optional<String> getPpidAlias(final String path, final String aliasKey, final String idKey) throws IOException, JSONException {
        return giftCloudSession.requestOptional(new HttpRequestWithoutOutput<String>(HttpConnection.ConnectionType.GET, path, new HttpJsonPpidResponseProcessor(new JSONAliasesExtractor(aliasKey, idKey)), giftCloudProperties, reporter));
    }

    public <T> Optional<T> getUsingJsonExtractor(final String query) throws IOException {
        return giftCloudSession.requestOptional(new HttpRequestWithoutOutput<Optional<T>>(HttpConnection.ConnectionType.GET, query, new HttpJsonResponseProcessor(new JSONConfigurationExtractor()), giftCloudProperties, reporter));
    }

    public String getString(final String path) throws IOException {
        return giftCloudSession.request(new HttpRequestWithoutOutput<String>(HttpConnection.ConnectionType.GET, path, new HttpStringResponseProcessor(), giftCloudProperties, reporter));
    }

    public Set<String> getStringList(final String path) throws IOException {
        return giftCloudSession.request(new HttpRequestWithoutOutput<Set<String>>(HttpConnection.ConnectionType.GET, path, new HttpStringListResponseProcessor(), giftCloudProperties, reporter));
    }

    public <ApplicatorT> ApplicatorT getApplicator(final String path, final ScriptApplicatorFactory<ApplicatorT> factory) throws IOException {
        return giftCloudSession.request(new HttpRequestWithoutOutput<ApplicatorT>(HttpConnection.ConnectionType.GET, path, new HttpApplicatorResponseProcessor(factory), giftCloudProperties, reporter));
    }

    public void uploadEcat(final String path, final String projectName, final String sessionId, final String subjectLabel, final File file) throws IOException {
        giftCloudSession.request(new EcatUploadPostRequest(path, file, projectName, subjectLabel, sessionId, giftCloudProperties, reporter));
    }

    public String getStringFromStream(final String path, final InputStream xmlStream) throws IOException {
        return giftCloudSession.request(new XmlStreamPostRequestWithStringResponse(path, xmlStream, giftCloudProperties, reporter));
    }

    public String sendSessionVariables(final String path, final SessionParameters sessionParameters) throws IOException {
        return giftCloudSession.request(new JSONRequestConnectionProcessor(sessionParameters, path, giftCloudProperties, reporter));
    }

    public Set<String> appendFileUsingZipUpload(final String relativeUrl, final ZipSeriesRequestFactory.ZipStreaming zipStreaming, final FileCollection fileCollection, Iterable<ScriptApplicator> applicators, UploadStatisticsReporter progress) throws IOException {
        return giftCloudSession.request(ZipSeriesRequestFactory.build(HttpConnection.ConnectionType.PUT, zipStreaming, relativeUrl, fileCollection, applicators, progress, new HttpEmptyResponseProcessor(), giftCloudProperties, reporter));
    }

    public Set<String> uploadSeriesUsingZipUpload(final String relativeUrl, final ZipSeriesRequestFactory.ZipStreaming zipStreaming, final FileCollection fileCollection, final Iterable<ScriptApplicator> applicators, final UploadStatisticsReporter progress) throws IOException {
        return giftCloudSession.request(ZipSeriesRequestFactory.build(HttpConnection.ConnectionType.POST, zipStreaming, relativeUrl, fileCollection, applicators, progress, new HttpSetResponseProcessor(), giftCloudProperties, reporter));
    }

    public void resetCancellation() {
        giftCloudSession.resetCancellation();
    }

    public void createResource(final String relativeUrl) throws IOException {
        giftCloudSession.request(new HttpRequestWithoutOutput<String>(HttpConnection.ConnectionType.PUT, relativeUrl, new HttpStringResponseProcessor(), giftCloudProperties, reporter));
    }

    public void createPostResource(final String relativeUrl) throws IOException {
        giftCloudSession.request(new HttpRequestWithoutOutput<String>(HttpConnection.ConnectionType.POST, relativeUrl, new HttpStringResponseProcessor(), giftCloudProperties, reporter));
    }
}
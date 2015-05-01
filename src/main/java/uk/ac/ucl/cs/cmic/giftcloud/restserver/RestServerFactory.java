package uk.ac.ucl.cs.cmic.giftcloud.restserver;

import uk.ac.ucl.cs.cmic.giftcloud.util.GiftCloudReporter;

import java.net.MalformedURLException;

public interface RestServerFactory {
    GiftCloudUploaderRestServer create(String giftCloudServerUrlString, GiftCloudProperties giftCloudProperties, GiftCloudReporter reporter) throws MalformedURLException;
}

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

package uk.ac.ucl.cs.cmic.giftcloud.uploadapp;


import netscape.javascript.JSObject;
import uk.ac.ucl.cs.cmic.giftcloud.restserver.MultiUploadReporter;
import org.slf4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;

public class GiftCloudReporter implements MultiUploadReporter {

    private Container container;

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(GiftCloudReporter.class);


    public GiftCloudReporter(Container container) {
        this.container = container;
        configureLogging();
    }

    @Override
    public void errorBox(final String errorMessage, final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter writer = new PrintWriter(sw);
        writer.println(errorMessage);
        writer.println("Error details:");
        throwable.printStackTrace(writer);
        final JTextArea text = new JTextArea(sw.toString());
        text.setEditable(false);
        container.add(text);
        container.validate();
    }

    @Override
    public void loadWebPage(String url) throws MalformedURLException {
//        container.getAppletContext().showDocument(new URL(url));
    }

    @Override
    public void exit() {
        final JSObject context = getJSContext();
        if (null == context) {

            warn("Unable to retrieve JavaScript window context, possibly running in non-browser-hosted mode like appletviewer?");

            System.err.println("javascript close failed");
            // this usually means we're in a non-browser applet viewer
        } else {
            context.call("close", null);
        }
    }

    @Override
    public Container getContainer() {
        return container;
    }

    /**
     * Retrieves the Javascript object context if available.
     *
     * @return The Javascript object if available. Returns null if not available (e.g. if running in a debugger or
     *         non-Javascript-enabled browser.
     */
    public JSObject getJSContext() {
        return null;
    }

    @Override
    public void trace(String msg) {
        logger.trace(msg);
    }

    @Override
    public void trace(String format, Object arg) {
        logger.trace(format, arg);
    }

    @Override
    public void error(String msg, Throwable t) {
        logger.error(msg, t);
    }

    @Override
    public void info(String msg) {
        logger.info(msg);
    }

    @Override
    public void info(String msg, Throwable t) {
        logger.info(msg, t);
    }

    @Override
    public void warn(String msg) {
        logger.warn(msg);

    }

    @Override
    public void debug(String msg) {
        logger.debug(msg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        logger.debug(format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        logger.trace(format, arg1, arg2);
    }

    @Override
    public void debug(String msg, Throwable t) {
        logger.debug(msg, t);
    }

    @Override
    public void info(String format, Object arg) {
        logger.info(format, arg);
    }

    @Override
    public void error(String format, Object arg) {
        logger.error(format, arg);
    }

    @Override
    public void error(String message) {
        logger.error(message);
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public boolean askRetry(Component parentComponent, String title, String message) {
        final Object[] options = {"Retry", "Cancel"};
        final int n = JOptionPane.showOptionDialog(parentComponent,
                message,
                title,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE,
                null,
                options,
                options[0]);
        return (JOptionPane.NO_OPTION != n);
    }

    /**
     * Loads logging resources, including loading logging properties from custom URLs specified by the
     * LOG4J_PROPS_URL applet parameter.
     */
    private void configureLogging() {
    }
}
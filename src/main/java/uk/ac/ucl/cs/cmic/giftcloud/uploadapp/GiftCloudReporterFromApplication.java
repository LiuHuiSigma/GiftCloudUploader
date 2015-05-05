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


import com.pixelmed.display.DialogMessageLogger;
import com.pixelmed.display.SafeCursorChanger;
import com.pixelmed.display.event.StatusChangeEvent;
import com.pixelmed.event.ApplicationEventDispatcher;
import com.pixelmed.utils.MessageLogger;
import netscape.javascript.JSObject;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import uk.ac.ucl.cs.cmic.giftcloud.Progress;
import uk.ac.ucl.cs.cmic.giftcloud.uploader.GiftCloudException;
import uk.ac.ucl.cs.cmic.giftcloud.util.GiftCloudReporter;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.util.Optional;

public class GiftCloudReporterFromApplication implements GiftCloudReporter, MessageLogger, Progress {

    private final Container container;
    private final GiftCloudDialogs giftCloudDialogs;

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(GiftCloudReporterFromApplication.class);

    protected final SafeCursorChanger cursorChanger;
    protected final MessageLogger messageLogger;

    private final ProgressModel progressModel = new ProgressModel();

    public GiftCloudReporterFromApplication(final Container container, final GiftCloudDialogs giftCloudDialogs) {
        this.container = container;
        this.giftCloudDialogs = giftCloudDialogs;
        configureLogging();
        messageLogger = new DialogMessageLogger("GIFT-Cloud Log", 512, 384, false/*exitApplicationOnClose*/, false/*visible*/);
        cursorChanger = new SafeCursorChanger(container);
    }

    private void errorBox(final String errorMessage, final Optional<String> additionalText) {
        final StringWriter sw = new StringWriter();
        final PrintWriter writer = new PrintWriter(sw);
        writer.println(errorMessage);
        writer.println("Error details:");
        writer.println(additionalText);
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
            context.call("close", (Object) null);
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
     * non-Javascript-enabled browser.
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

    @Override
    public void reportErrorToUser(String errorText, Throwable throwable) {
        String finalErrorText;
        String combinedErrorText;
        Optional<String> additionalText = Optional.empty();

        if (throwable instanceof GiftCloudException) {
            finalErrorText = throwable.getLocalizedMessage();
            combinedErrorText = finalErrorText;
        } else {
            combinedErrorText = errorText + " " + throwable.getLocalizedMessage();
            finalErrorText = errorText;
            additionalText = Optional.of(throwable.getLocalizedMessage());
        }
        errorBox(finalErrorText, additionalText);
        updateStatusText(combinedErrorText);
        error(combinedErrorText);
        throwable.printStackTrace(System.err);
    }

    @Override
    public void silentWarning(final String warning) {
        logger.info(warning);
    }

    @Override
    public void silentLogException(final Throwable throwable, final String errorMessage) {
        logger.info(errorMessage + ":" + throwable.getLocalizedMessage());
    }

    /**
     * Loads logging resources
     */
    private void configureLogging() {
        PropertyConfigurator.configure(this.getClass().getClassLoader().getResource("uk/ac/ucl/cs/cmic/giftcloud/log4j.properties"));
    }

    public void setWaitCursor() {
        cursorChanger.setWaitCursor();
    }

    public void restoreCursor() {
        cursorChanger.restoreCursor();
    }


    public void addProgressListener(final Progress progress) {
        progressModel.addListener(progress);
    }

    @Override
    public void sendLn(String message) {
        messageLogger.sendLn(message);
    }

    @Override
    public void send(String message) {
        messageLogger.send(message);
    }

    public void showMesageLogger() {
        if (logger instanceof DialogMessageLogger) {
            ((DialogMessageLogger) logger).setVisible(true);
        }
    }

    public void showError(final String errorMessage) {
        giftCloudDialogs.showError(errorMessage);
    }

    public void startProgressBar(int maximum) {
        progressModel.startProgress(maximum);
    }

    public void startProgressBar() {
        progressModel.startProgress();
    }

    public void updateProgressBar(int value) {
        progressModel.updateProgressBar(value);
    }

    public void updateProgressBar(int value, int maximum) {
        progressModel.updateProgressBar(value, maximum);
    }

    public void endProgressBar() {
        progressModel.endProgressBar();
    }

    @Override
    public void updateStatusText(String progressText) {
        progressModel.updateProgressText(progressText);
//        messageLogger.sendLn(progressText);
        ApplicationEventDispatcher.getApplicationEventDispatcher().processEvent(new StatusChangeEvent(progressText));
    }

    @Override
    public boolean isCancelled() {
        return progressModel.isCancelled();
    }


    // These are the preferred methods for reporting to the user

    public void silentError(final String errorMessage, final Throwable throwable) {
        if (throwable == null) {
            messageLogger.sendLn(errorMessage);
        } else {
            messageLogger.sendLn(errorMessage + " with exception:" + throwable.getLocalizedMessage());
        }
    }

    public void warnUser(final String warningMessage) {
        giftCloudDialogs.showMessage(warningMessage);
    }


}
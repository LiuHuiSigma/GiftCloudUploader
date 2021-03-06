/* Copyright (c) 2001-2013, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package com.pixelmed.network;

import com.pixelmed.dicom.DicomException;
import com.pixelmed.dicom.StoredFilePathStrategy;
import uk.ac.ucl.cs.cmic.giftcloud.util.GiftCloudException;
import uk.ac.ucl.cs.cmic.giftcloud.util.GiftCloudUploaderError;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * <p>This class waits for incoming connections and association requests for
 * the SCP role of SOP Classes of the Storage Service Class,
 * the Study Root Query Retrieve Information Model Find, Get and Move SOP Classes,
 * and the Verification SOP Class.</p>
 *
 * <p>The class has a constructor and a <code>run()</code> method. The
 * constructor is passed a socket on which to listen for transport
 * connection open indications. The <code>run()</code> method waits
 * for transport connection open indications, then instantiates
 * {@link com.pixelmed.network.StorageSOPClassSCP StorageSOPClassSCP}
 * to accept an association and wait for storage or verification commands, storing
 * data sets in Part 10 files in the specified folder.</p>
 *
 * <p>An instance of {@link com.pixelmed.network.ReceivedObjectHandler ReceivedObjectHandler}
 * can be supplied in the constructor to process the received data set stored in the file
 * when it has been completely received.</p>
 *
 * <p>If it is necessary to shutdown the StorageSOPClassSCPDispatcher, for example after changing the
 * properties that define the listening port or AE Title, the 
 * {@link com.pixelmed.network.StorageSOPClassSCPDispatcher#shutdown() shutdown()}
 * method can be called.</p>
 *
 * <p>Debugging messages with a varying degree of verbosity can be activated.</p>
 *
 * <p>The main method is also useful in its own right as a command-line Storage
 * SCP utility, which will store incoming files in a specified directory.</p>
 *
 * <p>For example, on Unix:</p>
 * <pre>
% java -cp ./pixelmed.jar com.pixelmed.network.StorageSOPClassSCPDispatcher "104" "STORESCP" "/tmp" 0
 * </pre>
 *
 * <p>On Windows, the classpath syntax would use a different separator, e.g. <code>.\pixelmed.jar</code></p>
 *
 * <p>Note that the main method can also be used without command line arguments, in which case it looks
 * for a properties file or uses defaults (refer to the main() method documentation for details).</p>
 *
 * @see com.pixelmed.network.StorageSOPClassSCP
 * @see com.pixelmed.network.ReceivedObjectHandler
 *
 * @author	dclunie
 */
public class StorageSOPClassSCPDispatcher implements Runnable {
	/***/
	private static final String identString = "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/network/StorageSOPClassSCPDispatcher.java,v 1.45 2014/09/09 20:34:09 dclunie Exp $";
	
	private int timeoutBeforeCheckingForInterrupted = 5000;	// in mS ... should be a property :(

	/***/
	private class DefaultReceivedObjectHandler extends ReceivedObjectHandler {
		/**
		 * @param	fileName
		 * @param	transferSyntax		the transfer syntax in which the data set was received and is stored
		 * @param	callingAETitle		the AE title of the caller who sent the data set
		 * @throws	IOException
		 * @throws	DicomException
		 * @throws	DicomNetworkException
		 */
		public void sendReceivedObjectIndication(String fileName,String transferSyntax,String callingAETitle)
				throws DicomNetworkException, DicomException, IOException {
System.err.println("StorageSOPClassSCPDispatcher.DefaultReceivedObjectHandler.sendReceivedObjectIndication() fileName: "+fileName+" from "+callingAETitle+" in "+transferSyntax);
		}
	}
	
	/***/
	private int port;
	/***/
	private String calledAETitle;
	/***/
	private int ourMaximumLengthReceived;
	/***/
	private int socketReceiveBufferSize;
	/***/
	private int socketSendBufferSize;
	/***/
	private File savedImagesFolder;
	/***/
	protected StoredFilePathStrategy storedFilePathStrategy;
	/***/
	private ReceivedObjectHandler receivedObjectHandler;
	/***/
	private AssociationStatusHandler associationStatusHandler;
	/***/
	private boolean secureTransport;
	/***/
	private PresentationContextSelectionPolicy presentationContextSelectionPolicy;
	/***/
	private int debugLevel;
	/***/
	private boolean wantToShutdown;

	private Thread mainThread = null;
	private Thread executingThread = null;
	private ServerSocket serverSocket = null;

	/**
	 * <p>Construct an instance of dispatcher that will wait for transport
	 * connection open indications, and handle associations and commands.</p>
	 *
	 * @param	port								the port on which to listen for connections
	 * @param	calledAETitle						our AE Title
	 * @param	savedImagesFolder					the folder in which to store received data sets (may be null, to ignore received data for testing)
	 * @param	storedFilePathStrategy				the strategy to use for naming received files and folders, or null for the default
	 * @param	receivedObjectHandler				the handler to call after each data set has been received and stored, or null for the default that prints the file name
	 * @param	presentationContextSelectionPolicy	which SOP Classes and Transfer Syntaxes to accept and reject, or null for the default
	 * @param	secureTransport						true if to use secure transport protocol
	 * @throws	IOException
	 */
	public StorageSOPClassSCPDispatcher(int port, String calledAETitle, File savedImagesFolder, StoredFilePathStrategy storedFilePathStrategy, ReceivedObjectHandler receivedObjectHandler,
										PresentationContextSelectionPolicy presentationContextSelectionPolicy,
										boolean secureTransport) throws IOException {
		this.port=port;
		this.calledAETitle=calledAETitle;
		this.savedImagesFolder=savedImagesFolder;
		this.storedFilePathStrategy=storedFilePathStrategy == null ? StoredFilePathStrategy.getDefaultStrategy() : storedFilePathStrategy;
		this.receivedObjectHandler=receivedObjectHandler == null ? new DefaultReceivedObjectHandler() : receivedObjectHandler;
		this.presentationContextSelectionPolicy=presentationContextSelectionPolicy == null ? new UnencapsulatedExplicitStoreFindMoveGetPresentationContextSelectionPolicy() : presentationContextSelectionPolicy;
		this.secureTransport=secureTransport;

		this.debugLevel=0;
		this.associationStatusHandler=null;
		this.ourMaximumLengthReceived=AssociationFactory.getDefaultMaximumLengthReceived();
		this.socketReceiveBufferSize=AssociationFactory.getDefaultReceiveBufferSize();
		this.socketSendBufferSize=AssociationFactory.getDefaultSendBufferSize();
	}


	/**
	 * Start a new thread with a new dispatcher
	 */
	public void startup() throws IOException {
		try {
			serverSocket = getServerSocket();
			mainThread = new Thread(this);
			mainThread.start();
		} catch (IOException e) {
			if (serverSocket != null) {
				try {
					serverSocket.close();
				} catch (IOException e2) {
				}
			}
			if (e instanceof java.net.BindException) {
				throw new GiftCloudException(GiftCloudUploaderError.PORT_ALREADY_IN_USE, e);
			}
			throw(e);
		}
	}

	/**
	 * <p>Request the dispatcher to stop listening and exit the thread.</p>
	 */
	public void shutdown() {
		wantToShutdown = true;
	}

	public void shutdownAndWait(final long maximumThreadCompletionWaitTime) {
		wantToShutdown = true;
		if (executingThread != null) {
			try {
				executingThread.join(maximumThreadCompletionWaitTime);
			} catch (InterruptedException e) {
			}
		}
		if (mainThread != null) {
			try {
				mainThread.join(maximumThreadCompletionWaitTime);
			} catch (InterruptedException e) {
			}
		}
	}

	/**
	 * <p>Waits for a transport connection indications, then spawns
	 * new threads to act as association acceptors, which then wait for storage or
	 * verification commands, storing data sets in Part 10 files in the specified folder, until the associations
	 * are released or the transport connections are closed.</p>
	 */
	public void run() {
		wantToShutdown = false;
		try {

			serverSocket.setSoTimeout(timeoutBeforeCheckingForInterrupted);
			while (!wantToShutdown) {
				try {
					Socket socket = serverSocket.accept();
					//setSocketOptions(socket,ourMaximumLengthReceived,socketReceiveBufferSize,socketSendBufferSize,debugLevel);
					try {
						executingThread = new Thread(new StorageSOPClassSCP(socket,calledAETitle,
							ourMaximumLengthReceived,socketReceiveBufferSize,socketSendBufferSize,savedImagesFolder,storedFilePathStrategy,
							receivedObjectHandler,associationStatusHandler,
								presentationContextSelectionPolicy,
							debugLevel));
						executingThread.start();
					}
					catch (Exception e) {
						e.printStackTrace(System.err);
					}
				}
				catch (SocketTimeoutException e) {
				}
			}
		}
		catch (IOException e) {
			// ToDo: need to report this back to the user
			e.printStackTrace(System.err);
		}
		try {
			if (serverSocket != null) {
				serverSocket.close();
			}
		}
		catch (Exception e) {
			e.printStackTrace(System.err);
		}
	}

	private ServerSocket getServerSocket() throws IOException {
		ServerSocket serverSocket;
		if (secureTransport) {
            SSLServerSocketFactory sslserversocketfactory = (SSLServerSocketFactory)SSLServerSocketFactory.getDefault();
            SSLServerSocket sslserversocket = (SSLServerSocket)sslserversocketfactory.createServerSocket(port);
            String[] suites = Association.getCipherSuitesToEnable(sslserversocket.getSupportedCipherSuites());
            if (suites != null) {
                sslserversocket.setEnabledCipherSuites(suites);
            }
            String[] protocols = Association.getProtocolsToEnable(sslserversocket.getEnabledProtocols());
            if (protocols != null) {
                sslserversocket.setEnabledProtocols(protocols);
            }
            serverSocket = sslserversocket;
        }
        else {
            serverSocket = new ServerSocket(port);
        }
		return serverSocket;
	}

}
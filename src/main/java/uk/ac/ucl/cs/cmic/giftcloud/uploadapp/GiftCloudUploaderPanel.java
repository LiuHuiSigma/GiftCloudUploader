package uk.ac.ucl.cs.cmic.giftcloud.uploadapp;

import com.pixelmed.database.DatabaseInformationModel;
import com.pixelmed.database.DatabaseTreeBrowser;
import com.pixelmed.database.DatabaseTreeRecord;
import com.pixelmed.dicom.DicomException;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ResourceBundle;
import java.util.Vector;

/**
 *
 */
public class GiftCloudUploaderPanel extends JPanel {

    private static int textFieldLengthForGiftCloudServerUrl = 32;

    // User interface components
    private final StatusPanel statusPanel;
    private final JComboBox<String> projectList;
    private final JPanel srcDatabasePanel;
    private final JTextField giftCloudServerText;
    private final QueryRetrievePanel remoteQueryRetrievePanel;

    // Callback to the controller for invoking actions
    private final GiftCloudUploaderController controller;

    // Models for data selections by the user
    private Vector<String> currentSourceFilePathSelections;

    // Error reporting interface
    private final GiftCloudReporterFromApplication reporter;

    public GiftCloudUploaderPanel(final Dialog dialog, final GiftCloudUploaderController controller, final ComboBoxModel<String> projectListModel, final DatabaseInformationModel srcDatabase, final GiftCloudPropertiesFromApplication giftCloudProperties, final ResourceBundle resourceBundle, final GiftCloudReporterFromApplication reporter) throws DicomException, IOException {
        super();
        this.controller = controller;
        this.reporter = reporter;

        remoteQueryRetrievePanel = new QueryRetrievePanel(dialog, controller, resourceBundle);

        srcDatabasePanel = new JPanel();
        srcDatabasePanel.setLayout(new GridLayout(1, 1));
        new OurSourceDatabaseTreeBrowser(srcDatabase, srcDatabasePanel);

        Border panelBorder = BorderFactory.createEtchedBorder();

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBorder(panelBorder);

        JButton configureButton = new JButton(resourceBundle.getString("configureButtonLabelText"));
        configureButton.setToolTipText(resourceBundle.getString("configureButtonToolTipText"));
        buttonPanel.add(configureButton);
        configureButton.addActionListener(new ConfigureActionListener());

        JButton importButton = new JButton(resourceBundle.getString("importButtonLabelText"));
        importButton.setToolTipText(resourceBundle.getString("importButtonToolTipText"));
        buttonPanel.add(importButton);
        importButton.addActionListener(new ImportActionListener());

        JButton importPacsButton = new JButton(resourceBundle.getString("importPacsButtonLabelText"));
        importPacsButton.setToolTipText(resourceBundle.getString("importPacsButtonToolTipText"));
        buttonPanel.add(importPacsButton);
        importPacsButton.addActionListener(new ImportPacsActionListener());

        JButton giftCloudUploadButton = new JButton(resourceBundle.getString("giftCloudUploadButtonLabelText"));
        giftCloudUploadButton.setToolTipText(resourceBundle.getString("giftCloudUploadButtonToolTipText"));
        buttonPanel.add(giftCloudUploadButton);
        giftCloudUploadButton.addActionListener(new GiftCloudUploadActionListener());

        JPanel projectUploadPanel = new JPanel();
        projectUploadPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        projectUploadPanel.setBorder(panelBorder);

        projectList = new JComboBox<String>();
        projectList.setEditable(false);
        projectList.setToolTipText(resourceBundle.getString("giftCloudProjectTooltip"));

        JLabel projectListLabel = new JLabel(resourceBundle.getString("giftCloudProjectLabelText"));
        projectUploadPanel.add(projectListLabel);
        projectUploadPanel.add(projectList);

        JLabel giftCloudServerLabel = new JLabel(resourceBundle.getString("giftCloudServerText"));
        giftCloudServerLabel.setToolTipText(resourceBundle.getString("giftCloudServerTextToolTipText"));


        giftCloudServerText = new AutoSaveTextField(giftCloudProperties.getGiftCloudUrl(), textFieldLengthForGiftCloudServerUrl) {
            @Override
            void autoSave() {
                giftCloudProperties.setGiftCloudUrl(getText());
            }
        };

        projectUploadPanel.add(giftCloudServerLabel);
        projectUploadPanel.add(giftCloudServerText);

        statusPanel = new StatusPanel();
        reporter.addProgressListener(statusPanel);

        {
            GridBagLayout mainPanelLayout = new GridBagLayout();
            setLayout(mainPanelLayout);
            {
                GridBagConstraints localBrowserPanesConstraints = new GridBagConstraints();
                localBrowserPanesConstraints.gridx = 0;
                localBrowserPanesConstraints.gridy = 0;
                localBrowserPanesConstraints.weightx = 1;
                localBrowserPanesConstraints.weighty = 1;
                localBrowserPanesConstraints.fill = GridBagConstraints.BOTH;
                mainPanelLayout.setConstraints(srcDatabasePanel,localBrowserPanesConstraints);
                add(srcDatabasePanel);
            }
            {
                GridBagConstraints buttonPanelConstraints = new GridBagConstraints();
                buttonPanelConstraints.gridx = 0;
                buttonPanelConstraints.gridy = 1;
                buttonPanelConstraints.fill = GridBagConstraints.HORIZONTAL;
                mainPanelLayout.setConstraints(buttonPanel,buttonPanelConstraints);
                add(buttonPanel);
            }
            {
                GridBagConstraints projectUploadPanelConstraints = new GridBagConstraints();
                projectUploadPanelConstraints.gridx = 0;
                projectUploadPanelConstraints.gridy = 3;
                projectUploadPanelConstraints.fill = GridBagConstraints.HORIZONTAL;
                mainPanelLayout.setConstraints(projectUploadPanel,projectUploadPanelConstraints);
                add(projectUploadPanel);
            }
            {
                GridBagConstraints statusBarPanelConstraints = new GridBagConstraints();
                statusBarPanelConstraints.gridx = 0;
                statusBarPanelConstraints.gridy = 6;
                statusBarPanelConstraints.fill = GridBagConstraints.HORIZONTAL;
                mainPanelLayout.setConstraints(statusPanel, statusBarPanelConstraints);
                add(statusPanel);
            }
        }

        projectList.setModel(projectListModel);
    }

    // Called when the database model has changed
    public void rebuildFileList(final DatabaseInformationModel srcDatabase) {
        srcDatabasePanel.removeAll();

        try {
            new OurSourceDatabaseTreeBrowser(srcDatabase, srcDatabasePanel);

        } catch (DicomException e) {
            // ToDo
            reporter.updateStatusText("Refresh of the file database failed: " + e);
            e.printStackTrace();
        }
        srcDatabasePanel.validate();
    }

    public QueryRetrieveRemoteView getQueryRetrieveRemoteView() {
        return remoteQueryRetrievePanel.getQueryRetrieveRemoteView();
    }

    private class ImportActionListener implements ActionListener {
		public void actionPerformed(ActionEvent event) {
            controller.selectAndImport();
        }
	}

    private class ImportPacsActionListener implements ActionListener {
        public void actionPerformed(ActionEvent event) {
            remoteQueryRetrievePanel.setVisible(true);
        }
    }

    private class GiftCloudUploadActionListener implements ActionListener {
        public void actionPerformed(ActionEvent event) {
            controller.upload(currentSourceFilePathSelections);
        }
    }

	private class ConfigureActionListener implements ActionListener {
		public void actionPerformed(ActionEvent event) {
			try {
                controller.showConfigureDialog();
			} catch (Exception e) {
				e.printStackTrace(System.err);
			}
		}
	}

    private class OurSourceDatabaseTreeBrowser extends DatabaseTreeBrowser {
        public OurSourceDatabaseTreeBrowser(DatabaseInformationModel d,Container content) throws DicomException {
            super(d,content);
        }

        protected boolean doSomethingWithSelections(DatabaseTreeRecord[] selections) {
            return false;	// still want to call doSomethingWithSelectedFiles()
        }

        protected void doSomethingWithSelectedFiles(Vector<String> paths) {
            currentSourceFilePathSelections = paths;
        }
    }

}

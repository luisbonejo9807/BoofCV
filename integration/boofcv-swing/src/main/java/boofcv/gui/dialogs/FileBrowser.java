/*
 * Copyright (c) 2011-2018, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.gui.dialogs;

import boofcv.gui.BoofSwingUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static javax.swing.text.DefaultCaret.ALWAYS_UPDATE;

/**
 * Dialog which lets the user selected a known file type and navigate the file system
 *
 * @author Peter Abeles
 */
public class FileBrowser extends JSpringPanel {
	// field containing the file name
	JTextArea fileName;
	// Path from root to current directory
	JComboBox directoryPath;
	// list of child files and directories
	JList fileList;
	DefaultListModel listModel = new DefaultListModel();

	// directory path
	List<File> directories = new ArrayList<>();

	ActionListener directoryListener;

	Listener listener;

	public FileBrowser( File directory , Listener listener) {
		this.listener = listener;

		directory = directory.getAbsoluteFile();
		if( directory.isDirectory() && directory.getName().equals(".")) {
			directory = directory.getParentFile();
		}
		fileName = new JTextArea();
		DefaultCaret caret = (DefaultCaret)fileName.getCaret();
		caret.setUpdatePolicy(ALWAYS_UPDATE);
		fileName.setRows(1);
		fileName.setEditable(false);
		fileName.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		JScrollPane nameScrollPane = new JScrollPane(fileName);
		nameScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		nameScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

		directoryPath = new JComboBox();
		fileList = new JList(listModel);
		fileList.setCellRenderer(new FileListCellRenderer());
		fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		fileList.setLayoutOrientation(JList.VERTICAL);
		fileList.addListSelectionListener(new FileSelectionListener(this));
		fileList.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent evt) {
				if (evt.getClickCount() == 2) {
					File selected = (File)listModel.get(fileList.getSelectedIndex());
					if( selected.isDirectory() ) {
						setDirectory(selected);
					} else {
						setSelected(selected);
						listener.handleClickedFile(selected);
					}
				}
			}});

		JScrollPane scrollList = new JScrollPane(fileList);
		scrollList.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);


		JPanel navigationPanel = createNavigationPanel();

//            fileName.setPreferredSize(new Dimension(1,25));
		JPanel directoryRow = new JPanel();
		directoryRow.setLayout(new BoxLayout(directoryRow, BoxLayout.X_AXIS));
		directoryRow.add(new JLabel("Location"));
		directoryRow.add(Box.createHorizontalStrut(5));
		directoryRow.add(directoryPath);

		constrainWestNorthEast(nameScrollPane,null,5,5);
		constrainWestNorthEast(directoryRow,nameScrollPane,5,5);
		constrainWestNorthEast(navigationPanel,directoryRow,5,5);
		constrainWestNorthEast(scrollList,navigationPanel,5,5);
		layout.putConstraint(SpringLayout.SOUTH, scrollList, -5, SpringLayout.SOUTH, this);

		setDirectory(directory);
		directoryListener = e->{
			if( directoryPath.getSelectedIndex() >= 0 ) {
				File f = directories.get(directoryPath.getSelectedIndex());
				setDirectory(f);
			}
		};
		directoryPath.addActionListener(directoryListener);
	}

	/**
	 *
	 * @see ListSelectionModel#SINGLE_SELECTION
	 * @see ListSelectionModel#SINGLE_INTERVAL_SELECTION
	 * @see ListSelectionModel#MULTIPLE_INTERVAL_SELECTION
	 *
	 * @param mode
	 */
	public void setSelectionMode( int mode) {
		fileList.setSelectionMode(mode);
	}
	private JPanel createNavigationPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

		JButton bHome = BoofSwingUtil.createButtonIconGUI("Home24.gif",26,26);
		bHome.setToolTipText("User Home");
		bHome.addActionListener(e->setDirectory(BoofSwingUtil.directoryUserHome()));

		JButton bSystem = BoofSwingUtil.createButtonIconGUI("Host24.gif",26,26);
		bSystem.setToolTipText("System");
		bSystem.addActionListener(e->setDirectory(null));

		JButton bPrevious = BoofSwingUtil.createButtonIconGUI("AlignCenter24.gif",26,26);
		bPrevious.setToolTipText("Previous");
//		bPrevious.addActionListener(e->setDirectory(defaultDirectory)); // TODO implement

		JButton bUp = BoofSwingUtil.createButtonIconGUI("Up24.gif",26,26);
		bUp.setToolTipText("Up Directory");
		bUp.addActionListener(e->{
			File f = new File(fileName.getText());
			setDirectory(f.getParentFile());
		});

		panel.add(Box.createHorizontalGlue());
		panel.add(bHome);
		panel.add(Box.createHorizontalStrut(5));
		panel.add(bSystem);
		panel.add(Box.createHorizontalStrut(5));
		panel.add(bPrevious);
		panel.add(Box.createHorizontalStrut(5));
		panel.add(bUp);
		panel.add(Box.createHorizontalGlue());

		return panel;
	}

	/**
	 * The selected file/directory has changed.  Just update the text
	 */
	public void setSelected( File file ) {
		fileName.setText(file.getAbsolutePath());
	}

	/**
	 * The parent directory has changed.  Update the file list.   If file is null then it's assumed to be
	 * the list of all devices. On unix there's only one which is / but on windows there can be multiple
	 */
	public void setDirectory( File file ) {

		List<File> roots = null;
		if( file == null ) {
			// Create a list of roots with something in them.  Windows list to list non-existant devices
			roots = new ArrayList<>(Arrays.asList(File.listRoots()));
			for (int i = roots.size()-1; i >= 0; i--) {
				File[]files = roots.get(i).listFiles();
				if( files == null || files.length == 0) {
					roots.remove(i);
				}
			}

			if( roots.size() == 1 ) {
				file = roots.get(0);
				roots = null;
			}
		}

		if( roots == null ) {
			setDirectoryNormal(file);
		} else {
			// Present the user with a list of file system roots
			fileName.setText("");

			listModel.clear();
			for (File f : roots) {
				listModel.addElement(f);
			}

			directoryPath.removeActionListener(directoryListener);
			directoryPath.removeAllItems();
			directoryPath.addActionListener(directoryListener);

			listener.handleSelectedFile(null);
		}
	}

	private void setDirectoryNormal(File file) {
		fileName.setText(file.getAbsolutePath());

		listModel.clear();
		File[] fileArray = file.listFiles();
		List<File> files = fileArray == null ? new ArrayList<>() : Arrays.asList(fileArray);
		Collections.sort(files);
		for (File f : files) {
			if( f.isHidden() )
				continue;

			listModel.addElement(f);
		}

		file = file.getAbsoluteFile();
		if( file.isFile() )
			file = file.getParentFile();
		files = new ArrayList<>();
		while( file != null ) {
			files.add(file);
			file = file.getParentFile();
		}

		directoryPath.removeActionListener(directoryListener);
		directoryPath.removeAllItems();
		directories.clear();
		for (int i = files.size()-1; i >=0; i--) {
			File f = files.get(i);
			if( f.getParentFile() == null ) {
				try {
					directoryPath.addItem(f.getCanonicalPath());
				} catch (IOException e) {
					directoryPath.addItem("/");
				}
			} else
				directoryPath.addItem( files.get(i).getName() );
			directories.add(f);
		}
		directoryPath.setSelectedIndex( files.size()-1 );
		directoryPath.addActionListener(directoryListener);

		listener.handleSelectedFile(null);
	}

	public List<File> getSelectedFiles() {
		List selected = fileList.getSelectedValuesList();

		List<File> out = new ArrayList<>();
		for (int i = 0; i < selected.size(); i++) {
			out.add( (File)selected.get(i));
		}
		return out;
	}

	/**
	 * Needed to add System icons for each type of file
	 */
	private class FileListCellRenderer extends DefaultListCellRenderer {

		private FileSystemView fileSystemView;
		private JLabel label;
		private Color textSelectionColor = Color.BLACK;
		private Color backgroundSelectionColor = Color.CYAN;
		private Color textNonSelectionColor = Color.BLACK;
		private Color backgroundNonSelectionColor = Color.WHITE;

		FileListCellRenderer() {
			label = new JLabel();
			label.setBorder(new EmptyBorder(2,4,2,4));
			label.setOpaque(true);
			fileSystemView = FileSystemView.getFileSystemView();
		}

		@Override
		public Component getListCellRendererComponent(
				JList list,
				Object value,
				int index,
				boolean selected,
				boolean expanded) {


			File file = (File)value;
			String name = fileSystemView.getSystemDisplayName(file);
			if( name.length() == 0 )
				name = file.getAbsolutePath();
			label.setIcon(fileSystemView.getSystemIcon(file));
			label.setText(name);
			label.setToolTipText(file.getPath());

			if (selected) {
				label.setBackground(backgroundSelectionColor);
				label.setForeground(textSelectionColor);
			} else {
				label.setBackground(backgroundNonSelectionColor);
				label.setForeground(textNonSelectionColor);
			}

			return label;
		}
	}

	/**
	 * Handles changes in which file is selected
	 */
	private class FileSelectionListener implements ListSelectionListener {

		FileBrowser browser;

		public FileSelectionListener(FileBrowser browser) {
			this.browser = browser;
		}

		@Override
		public void valueChanged(ListSelectionEvent e) {
			if( e.getValueIsAdjusting() )
				return;

			JList fileList = (JList)e.getSource();
			DefaultListModel listModel = (DefaultListModel)fileList.getModel();

			int index = fileList.getSelectedIndex();
			if( index >= 0 ) {
				File f = (File)listModel.getElementAt(index);
				browser.setSelected(f);
				listener.handleSelectedFile(f);
			} else {
				listener.handleSelectedFile(null);
			}
		}
	}

	public interface Listener {
		void handleSelectedFile( File file );

		void handleClickedFile( File file );
	}
}

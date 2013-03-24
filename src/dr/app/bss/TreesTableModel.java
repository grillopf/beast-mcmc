package dr.app.bss;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;

import dr.evolution.util.Taxon;
import dr.evomodel.tree.TreeModel;

@SuppressWarnings("serial")
public class TreesTableModel extends AbstractTableModel {

	private PartitionDataList dataList;
	private MainFrame frame;

	public static String[] COLUMN_NAMES = { "Tree File", "Taxa", "Trees" };
	private static final Class<?>[] COLUMN_TYPES = new Class<?>[] {
			JButton.class, Integer.class, Integer.class };

	public final static int TREE_FILE_INDEX = 0;
	public final static int TAXA_INDEX = 1;
	public final static int TREES_INDEX = 2;

	public TreesTableModel(PartitionDataList dataList, MainFrame frame) {
		this.dataList = dataList;
		this.frame = frame;
	}// END: Constructor

	public void addDefaultRow() {
		dataList.treeFileList.add(new File(""));
		fireTableRowsInserted(dataList.treeFileList.size() - 1,
				dataList.treeFileList.size() - 1);
	}

	public void deleteRow(int row) {
		dataList.treeFileList.remove(row);
		fireTableDataChanged();
	}

	@Override
	public int getColumnCount() {
		return COLUMN_NAMES.length;
	}

	@Override
	public int getRowCount() {
		return dataList.treeFileList.size();
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return COLUMN_TYPES[columnIndex];
	}// END: getColumnClass

	public boolean isCellEditable(int row, int column) {
		switch (column) {
		case TREE_FILE_INDEX:
			return false;
		case TAXA_INDEX:
			return false;
		case TREES_INDEX:
			return false;
		default:
			return false;
		}
	}// END: isCellEditable

	public String getColumnName(int column) {
		return COLUMN_NAMES[column];
	}// END: getColumnName

	@Override
	public Object getValueAt(int row, int column) {
		switch (column) {
		case TREE_FILE_INDEX:

			JButton treeFileButton = new JButton(Utils.CHOOSE_FILE);
			treeFileButton.addActionListener(new ListenLoadTreeFile(row));
			return treeFileButton;

		case TAXA_INDEX:
			return 0;

		case TREES_INDEX:
			return 0;

		default:
			return "Error";
		}
	}// END: getValueAt

	public void setValueAt(Object value, int row, int column) {

		switch (column) {

		case TREE_FILE_INDEX:
			dataList.get(row).treeFile = (File) value;
			break;

		case TAXA_INDEX:
			break;

		case TREES_INDEX:
			break;

		default:
			throw new RuntimeException("Invalid index.");

		}// END: switch

		fireTableCellUpdated(row, column);

	}// END: setValueAt

	private class ListenLoadTreeFile implements ActionListener {

		private int row;

		public ListenLoadTreeFile(int row) {
			this.row = row;
		}// END: Constructor

		public void actionPerformed(ActionEvent ev) {

			doLoadTreeFile(row);

		}// END: actionPerformed
	}// END: ListenLoadTreeFile

	private void doLoadTreeFile(int row) {

		try {

			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle("Select trees file...");
			chooser.setMultiSelectionEnabled(false);
			chooser.setCurrentDirectory(frame.getWorkingDirectory());

			int returnValue = chooser.showOpenDialog(Utils.getActiveFrame());

			if (returnValue == JFileChooser.APPROVE_OPTION) {

				File file = chooser.getSelectedFile();

				if (file != null) {

					// check if file unique, throw exception if not
					if (!isFileInList(file)) {

						loadTreeFile(file, row);

					} else {
						throw new RuntimeException("File with this name already loaded.");
					}

					File tmpDir = chooser.getCurrentDirectory();
					if (tmpDir != null) {
						frame.setWorkingDirectory(tmpDir);
					}

				}// END: file opened check
			}// END: dialog cancelled check

		} catch (Exception e) {
			Utils.handleException(e);
		}// END: try-catch block

	}// END: doLoadTreeFile

	private boolean isFileInList(File file) {
		boolean exists = false;

		for (File file2 : dataList.treeFileList) {

			if (file.getName()
					.equalsIgnoreCase(file2.getName())) {
				exists = true;
				break;
			}

		}

		return exists;
	}// END: isFileInList

	private void loadTreeFile(final File file, final int row) {

		frame.setBusy();
		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {

			// Executed in background thread
			public Void doInBackground() {

				try {

					setTreeFile(file, row);

					TreeModel tree = Utils.importTreeFromFile(file);
					for (Taxon taxon : tree.asList()) {

						// set attributes to be parsed later in Taxa panel
						if (!Utils.taxonExists(taxon, dataList.taxonList)) {

							double absoluteHeight = Utils
									.getAbsoluteTaxonHeight(taxon, tree);

							taxon.setAttribute(Utils.ABSOLUTE_HEIGHT,
									absoluteHeight);

							taxon.setAttribute(Utils.TREE_FILENAME,
									file.getName());

							dataList.taxonList.addTaxon(taxon);

						}// END: taxon exists check

					}// END: taxon loop

				} catch (Exception e) {
					Utils.handleException(e);
				}// END: try-catch block

				return null;
			}// END: doInBackground()

			// Executed in event dispatch thread
			public void done() {
				frame.setIdle();
				frame.fireTaxaChanged();
			}// END: done
		};

		worker.execute();

	}// END: loadTreeFile

	private void setTreeFile(File file, int row) {
		dataList.treeFileList.remove(row);
		dataList.treeFileList.add(row, file);
		// dataList.treeFileList.add(file);
	}

	public void setDataList(PartitionDataList dataList) {
		this.dataList = dataList;
	}

}// END: class

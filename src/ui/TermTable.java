package ui;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.*;
import javax.swing.table.*;

import util.U;

public class TermTable {
	JTable table;
	JScrollPane scrollpane;
	Main.TermTableModel model;
	/** TermTable sends a *term* to this consumer. */
	Consumer<String> doubleClickListener;

	public JComponent top() { return scrollpane; }

	@SuppressWarnings("serial")
	public TermTable(Main.TermTableModel ttm) {
		model = ttm;
		table = new JTable(model); 
//		{
//			public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
//				//Always toggle on single selection
//				super.changeSelection(rowIndex, columnIndex, toggle && !extend, extend);
//			}
//		};
		scrollpane = new JScrollPane(table);

	}

	List<String> getSelectedTerms() {
		List<String> terms = new ArrayList<>();
		for (int row : table.getSelectedRows()) {
			terms.add(getTermAt(row));
		}
		return terms;
	}

	void setupTermTable() {
		table.setFillsViewportHeight(true);

		TermCellRenderer centerRenderer = new TermCellRenderer();
		centerRenderer.setHorizontalAlignment( JLabel.CENTER );
		TermCellRenderer rightRenderer = new TermCellRenderer();
		rightRenderer.setHorizontalAlignment( JLabel.RIGHT );

		TableColumn cc;
		cc = table.getColumnModel().getColumn(0);
		cc.setMinWidth(100);
		cc = table.getColumnModel().getColumn(1); cc.setMinWidth(20); cc.setWidth(20);
		cc.setCellRenderer(centerRenderer);
		cc = table.getColumnModel().getColumn(2); cc.setMinWidth(8); cc.setMaxWidth(8);
		cc = table.getColumnModel().getColumn(3); cc.setMinWidth(20); cc.setWidth(20);
		cc.setCellRenderer(centerRenderer);
		cc = table.getColumnModel().getColumn(4);
		cc.setCellRenderer(centerRenderer);
		cc.setMinWidth(50);

		table.setAutoCreateRowSorter(true);

		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				JTable table = (JTable) e.getSource();
				int row = table.rowAtPoint(e.getPoint());
				if (row < 0) {
					// seems to use -1 if not a row
					return;
				}
				U.p("is selected? " + table.getSelectionModel().isSelectedIndex(row));

				if (e.getClickCount()==1) {
					//					if (!table.getSelectionModel().isSelectedIndex(row)) {
					//						table.getSelectionModel().removeIndexInterval(row,row);	
					//					}
				}
				else if (e.getClickCount()==2) {
					String term = getTermAt(row);
					doubleClickListener.accept(term);
				}
			}
		});


	}

	public String getTermAt(int row) {
		return (String) table.getValueAt(row,0);
	}

	public void updateCalculations() {
		for (int row=0; row<table.getRowCount(); row++) {
			for (int col=1; col<table.getColumnCount(); col++) {
				model.fireTableCellUpdated(row,col);
			}
		}

	}

}


@SuppressWarnings("serial")
class TermCellRenderer extends DefaultTableCellRenderer {
	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
		Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		if (column==4) {
			JLabel label = (JLabel) c;
			String text = String.format("%.3f", (Number) value);
			label.setText(text);
		}
		return c;
	}

}


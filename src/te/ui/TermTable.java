package te.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
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
	TermTableModel model;
	/** TermTable sends a *term* to this consumer. */
	Consumer<String> doubleClickListener;

	public JComponent top() { return scrollpane; }

	public TermTable(TermTableModel ttm) {
		model = ttm;
		table = new JTable(model) {
			{
//				setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
//				setPreferredScrollableViewportSize(new Dimension(250,200));
				setFillsViewportHeight(true);
				// doesnt seem to do anything
//				 setMaximumSize(new Dimension(100,100000));
			}
			@Override public Dimension getPreferredSize() {
//				U.p("jtable prefsize " + super.getPreferredSize());
				return super.getPreferredSize();
			}
			// this doesnt seem to do anything
			@Override public boolean getScrollableTracksViewportWidth() { return true; }
		};

//		{
//			public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
//				//Always toggle on single selection
//				super.changeSelection(rowIndex, columnIndex, toggle && !extend, extend);
//			}
//		};
		
		scrollpane = new JScrollPane(table) {
			@Override public Dimension getSize() {
//				U.p("scrollpane size " + super.getSize());
				return super.getSize();
			}
			@Override public Dimension getPreferredSize() {
//				U.p("scrollpane prefsize " + super.getPreferredSize());
				return super.getPreferredSize();
			}
		};
//		U.p("init scrollpane prefsize " + scrollpane.getPreferredSize());
		scrollpane.setPreferredSize(new Dimension(250,200));
	}

	List<String> getSelectedTerms() {
//		U.p("jtable prefsize "+table.getPreferredSize() + " \tscrollprefsize "+table.getPreferredScrollableViewportSize()
//				+ " \tscrollpane's prefsize "+scrollpane.getPreferredSize());

		List<String> terms = new ArrayList<>();
		for (int row : table.getSelectedRows()) {
			terms.add(getTermAt(row));
		}
		return terms;
	}

	void setupTermTable() {
		table.setSelectionBackground(GUtil.Dark2[0]);
		
//		U.p("prefsize "+table.getPreferredSize() + " scrollprefsize "+table.getPreferredScrollableViewportSize()
//				+ " scrollpane's prefsize "+scrollpane.getPreferredSize());


		TermCellRenderer centerNumberRenderer = new TermCellRenderer();
		centerNumberRenderer.setHorizontalAlignment( JLabel.CENTER );
//		TermCellRenderer rightRenderer = new TermCellRenderer();
//		rightRenderer.setHorizontalAlignment( JLabel.RIGHT );

		TableColumn cc;
		cc = table.getColumnModel().getColumn(0);
			cc.setMinWidth(100);
//			cc.setMaxWidth(100);
			cc.setPreferredWidth(100);
		cc = table.getColumnModel().getColumn(1);
			cc.setCellRenderer(centerNumberRenderer);
//			cc.setMinWidth(50);
//			cc.setMaxWidth(50);
			cc.setPreferredWidth(50);
		cc = table.getColumnModel().getColumn(2);
			cc.setMinWidth(8);
			cc.setMaxWidth(8);
			cc.setPreferredWidth(8);
		cc = table.getColumnModel().getColumn(3);
		cc.setCellRenderer(centerNumberRenderer);
//			cc.setMinWidth(50);
//			cc.setMaxWidth(50);
			cc.setPreferredWidth(50);
		cc = table.getColumnModel().getColumn(4);
			cc.setCellRenderer(centerNumberRenderer);
//			cc.setMinWidth(50);
//			cc.setMaxWidth(50);
			cc.setPreferredWidth(50);

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
//	@Override
//	public Dimension getPreferredSize() {
////		renderingComponent
//		return new Dimension(50,30);
//	}
	@Override
	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
		Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		if (column==4) {
			JLabel label = (JLabel) c;
			String text = String.format("%.2f", (Number) value);
			label.setText(text);
		}
		return c;
	}

}


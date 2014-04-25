package ui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
	
	public TermTable(Main.TermTableModel ttm) {
		model = ttm;
		table = new JTable(model);
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
		
		DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment( JLabel.CENTER );
		DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
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
				if (e.getClickCount()==2) {
					JTable table = (JTable) e.getSource();
					int row = table.rowAtPoint(e.getPoint());
					if (row < 0) {
						// seems to use -1 if not a row
						return;
					}
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



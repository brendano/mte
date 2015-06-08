package te.ui;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.table.AbstractTableModel;

import te.data.Analysis.TermvecComparison;

/** give this a termlist. it consults the global fcView for the terms' stats. */
public class TermTableModel extends AbstractTableModel {
	// these are lazy so can be swapped out or changed without this class needing to know
	Supplier<List<String>> terms;
	Supplier<TermvecComparison> comparison;  
	
	@Override
	public String getColumnName(int j) {
		return (new String[]{ "term", "local","","global", "lift" })[ j ];
	}
	@Override
	public int getRowCount() {
		return terms.get().size();
	}
	@Override
	public int getColumnCount() {
		return 5;
	}
	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		String t = terms.get().get(rowIndex);
		double epmi = comparison == null || comparison.get()==null ? 0 
								: comparison.get().epmi(t);
		switch (columnIndex) {
		case 0: return t;
		case 1: return (int) comparison.get().focus.value(t);
		case 2: return ":";
		case 3: return (int) comparison.get().background.value(t);
		case 4: return epmi;
		}
		assert false; return null;
	}
	@Override
	public Class<?> getColumnClass(int c) {
		switch (c) {
		case 0: return String.class;
		case 1: return Integer.class;
		case 2: return String.class;
		case 3: return Integer.class;
		case 4: return Double.class;
		}
		assert false; return null;
	}
}
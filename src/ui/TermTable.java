package ui;

import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.table.*;

import util.U;

import d.WeightedTerm;

public class TermTable {
	JTable table;
	JScrollPane scrollpane;
	Main.TermTableModel model;
	
	public TermTable(Main.TermTableModel ttm) {
		model = ttm;
		table = new JTable(model);
		scrollpane = new JScrollPane(table);
	}
	
}



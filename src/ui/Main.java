package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;

import util.U;

import d.Analysis;
import d.Corpus;
import d.DocSet;
import d.WeightedTerm;

interface QueryReceiver {
	public void receiveQuery(Collection<String> docids);
}

public class Main implements QueryReceiver {
	public Corpus corpus;
	public DocSet curDS;
	public List<WeightedTerm> topTerms = new ArrayList<>();
	TermTable  tt;
	TermTableModel ttModel;
	JLabel queryInfo;
	JTextField termProbThresh;
	
	void initData() {
		corpus = Corpus.load("/d/sotu/sotu.xy");
	}
	
	double getTermProbThresh() {
		String s = termProbThresh.getText();
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			return 1e-3;
		}
	}
	
	@Override
	public void receiveQuery(Collection<String> docids) {
		curDS = corpus.getDocSet(docids);
		topTerms = Analysis.topEPMI(500, getTermProbThresh(), curDS.terms, corpus.globalTerms);
		
		ttModel.fireTableDataChanged();
		refreshQueryInfo();
		
//		U.p("=== TOP WORDS ===");
//		for (WeightedTerm t : topTerms) {
//			U.pf("%-15s || %d vs %d || %.4g\n", 
//					t.term, 
//					(int) curDS.terms.value(t.term), (int) corpus.globalTerms.value(t.term),
//					t.weight);
//		}
				
	}
	
	void refreshQueryInfo() {
		String s = U.sf("Current selection: %s docs, %.0f wordtoks\n", curDS.docs.size(), curDS.terms.totalCount);
		queryInfo.setText(s);
	}
	
	@SuppressWarnings("serial")
	class TermTableModel extends AbstractTableModel {
		public TermTableModel() {
		}

		@Override
		public String getColumnName(int j) {
			return (new String[]{ "term", "local vs global count", "LR" })[ j ];
		}
		@Override
		public int getRowCount() {
			return topTerms.size();
		}

		@Override
		public int getColumnCount() {
			return 3;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			WeightedTerm t = topTerms.get(rowIndex);
			switch (columnIndex) {
			case 0:
				return t.term;
			case 1:
				return U.sf("%.0f vs %.0f", curDS.terms.value(t.term), corpus.globalTerms.value(t.term));
//			case 1:
//				return U.sf("%d", (int) curDS.terms.value(t.term));
//			case 3:
//				return U.sf("%d", (int) corpus.globalTerms.value(t.term));
//			case 2:
//				return "vs";
			case 2:
				return t.weight > 1 ? U.sf("%.2f", t.weight) : U.sf("%.4g", t.weight);
			}
			assert false; return null;
		}
		
	}
	void setupTermTable() {
        TableColumn cc;
        cc = tt.table.getColumnModel().getColumn(0);
        cc.setMinWidth(100);
        cc = tt.table.getColumnModel().getColumn(1); cc.setMinWidth(80); cc.setWidth(80);
//        cc = tt.table.getColumnModel().getColumn(2); cc.setMinWidth(20); cc.setMaxWidth(21);
//        cc = tt.table.getColumnModel().getColumn(3); cc.setMinWidth(20); cc.setWidth(20);
        cc = tt.table.getColumnModel().getColumn(2);
        cc.setMinWidth(50);
	}
	
	void go() {
		
        JFrame frame = new JFrame("Text Explorer Tool");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        
        JPanel bppanel = new JPanel();
        bppanel.setLayout(new BoxLayout(bppanel,BoxLayout.Y_AXIS));
        
        queryInfo = new JLabel();
        queryInfo.setPreferredSize(new Dimension(300,50));
        bppanel.add(queryInfo);
        
        BrushPanel brushPanel = new BrushPanel(this, corpus.allDocs());
        brushPanel.setMySize(500,300);
        brushPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        bppanel.add(brushPanel);
        
        ttModel = new TermTableModel();
        tt = new TermTable(ttModel);
        setupTermTable();
        
        JPanel termpanel = new JPanel();
        termpanel.setLayout(new FlowLayout());
        
        termProbThresh = new JTextField(".0005");
        termProbThresh.setPreferredSize(new Dimension(100,30));
        JPanel termprobPanel = new JPanel();
        termprobPanel.setLayout(new BoxLayout(termprobPanel, BoxLayout.X_AXIS));
        termprobPanel.add(new JLabel("Term prob thresh:"));
        termprobPanel.add(termProbThresh);
        termpanel.add(termprobPanel);
        
        termpanel.setPreferredSize(new Dimension(350,600));
        tt.table.setPreferredSize(new Dimension(350,500));
        tt.scrollpane.setPreferredSize(new Dimension(350,500));
        termpanel.add(tt.scrollpane);
        
        JTextArea docshower = new JTextArea("show stuff here");
        docshower.setPreferredSize(new Dimension(400,300));
        docshower.setEditable(false);
        bppanel.add(docshower);
        
        frame.getContentPane().add(bppanel,BorderLayout.NORTH);
        
        frame.setLayout(new FlowLayout());
        frame.setSize(600,600);
        frame.getContentPane().add(termpanel);
        frame.getContentPane().add(bppanel);

////////////////
        frame.addKeyListener(new KeyExiter());
        tt.scrollpane.addKeyListener(new KeyExiter());
        brushPanel.addKeyListener(new KeyExiter());

        //Display the window.
        frame.pack();
        frame.setVisible(true);

	}
	
	public static void main(String[] args) {
		final Main main = new Main();
		main.initData();
		SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                main.go();
            }
        });
	}

}


class KeyExiter implements KeyListener {
	@Override
	public void keyTyped(KeyEvent e) {
		if (e.getKeyChar() == 'q') {
			System.exit(0);
		}
	}
	@Override
	public void keyPressed(KeyEvent e) {
	}
	@Override
	public void keyReleased(KeyEvent e) {
	}
}
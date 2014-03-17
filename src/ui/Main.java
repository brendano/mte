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

import javax.swing.AbstractSpinnerModel;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.JSpinner.NumberEditor;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
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

@SuppressWarnings("serial")
public class Main implements QueryReceiver {
	public Corpus corpus;
	public DocSet curDS = new DocSet();
	public List<WeightedTerm> focusTerms = new ArrayList<>();
	TermTable  tt;
	TermTableModel ttModel;
	JLabel queryInfo;
	JLabel termlistInfo;
	JSpinner termProbThreshSpinner;
	JSpinner termCountThreshSpinner;
	
	void initData() {
		corpus = Corpus.load("/d/sotu/sotu.xy");
	}
	
	double getTermProbThresh() {
		return Double.parseDouble((String) termProbThreshSpinner.getValue());
	}
	int getTermCountThresh() {
		return (int) termCountThreshSpinner.getValue();
	}
	
	@Override
	public void receiveQuery(Collection<String> docids) {
		curDS = corpus.getDocSet(docids);
		refreshQueryInfo();
		
		refreshTermList();
		
				
	}
	
	void refreshTermList() {
		focusTerms = Analysis.topEPMI(getTermProbThresh(), getTermCountThresh(), curDS.terms, corpus.globalTerms);
		ttModel.fireTableDataChanged();
		termlistInfo.setText(U.sf("%d/%d terms", focusTerms.size(), curDS.terms.support().size()));
		
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
	
	class TermTableModel extends AbstractTableModel {
		public TermTableModel() {
		}

		@Override
		public String getColumnName(int j) {
			return (new String[]{ "term", "local:global", "LR" })[ j ];
		}
		@Override
		public int getRowCount() {
			return focusTerms.size();
		}

		@Override
		public int getColumnCount() {
			return 3;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			WeightedTerm t = focusTerms.get(rowIndex);
			switch (columnIndex) {
			case 0:
				return t.term;
			case 1:
				return U.sf("%.0f : %.0f", curDS.terms.value(t.term), corpus.globalTerms.value(t.term));
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
	
	static class MySM extends AbstractSpinnerModel {
		double curbase;
		int curmult;
		
		@Override
		public Object getValue() {
//			return curbase*curmult;
			return U.sf("%f",curbase*curmult);
		}

		@Override
		public void setValue(Object value) {
//			double x = (double) value;
			double x = Double.NEGATIVE_INFINITY;
			try {
				x = (double) Double.parseDouble((String)value);
				if (x==0) { U.p("WTF"); x=1e-10; }
			} catch (NumberFormatException e) {
				return;
			}
			
			curbase = Math.pow(10, Math.floor(Math.log10(x)));
			curmult = (int) Math.round(x/curbase);
			fireStateChanged();
		}

		@Override
		public Object getNextValue() {
			int newmult = curmult+1;
			double newbase = curbase;
			if (newmult >= 10) {
				newmult = 1;
				newbase *= 10;
			}
			return U.sf("%f",newbase*newmult);
		}

		@Override
		public Object getPreviousValue() {
			int newmult = curmult-1;
			double newbase = curbase;
			if (newmult<=0) {
				newmult=9;
				newbase /= 10;
			}
			return U.sf("%f",newbase*newmult);
		}
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
        termProbThreshSpinner = new JSpinner(new MySM());
        termProbThreshSpinner.setValue(".0005");
        termProbThreshSpinner.setPreferredSize(new Dimension(100,30));
        termProbThreshSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				refreshTermList();
			}
        });
        termCountThreshSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 1000, 1));
        termCountThreshSpinner.setPreferredSize(new Dimension(60,30));
        termCountThreshSpinner.setValue(1);
//        termCountThreshSpinner.setEditor(new JSpinner.NumberEditor(termCountThreshSpinner));
        termCountThreshSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				refreshTermList();
			}
        });

        JPanel termprobPanel = new JPanel();
        termprobPanel.setLayout(new BoxLayout(termprobPanel, BoxLayout.X_AXIS));
        termprobPanel.add(new JLabel("Term prob >="));
        termprobPanel.add(termProbThreshSpinner);
        termpanel.add(termprobPanel);
        
        termprobPanel.add(new JLabel("Term count >="));
        termprobPanel.add(termCountThreshSpinner);
        termlistInfo = new JLabel();
        termpanel.add(termlistInfo);
        
        termpanel.setPreferredSize(new Dimension(350,600));
//        tt.table.setPreferredSize(new Dimension(350,500));
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
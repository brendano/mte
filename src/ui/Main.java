package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.AbstractSpinnerModel;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.JSpinner.NumberEditor;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

import org.codehaus.jackson.JsonProcessingException;

import util.U;
import d.Analysis;
import d.Corpus;
import d.DocSet;
import d.Document;
import d.NLP;
import d.NLP.DocAnalyzer;
import d.TermQuery;
import d.WeightedTerm;
import edu.stanford.nlp.util.StringUtils;

interface QueryReceiver {
	public void receiveQuery(Collection<String> docids);
}

@SuppressWarnings("serial")
public class Main implements QueryReceiver {
	public Corpus corpus;
	public DocSet curDS = new DocSet();
	public DocSet termquery = null;
	
	public List<WeightedTerm> focusTerms = new ArrayList<>();
	TermTable  tt;
	TermTableModel ttModel;
	BrushPanel brushPanel;
	TextPanel textPanel;
	JLabel queryInfo;
	JLabel subqueryInfo;
	JLabel termlistInfo;
	JSpinner termProbThreshSpinner;
	JSpinner termCountThreshSpinner;
	JLabel termcountInfo;
	
	void initData() throws JsonProcessingException, IOException {
		corpus = Corpus.loadXY("/d/sotu/sotu.xy");
		corpus.loadNLP("/d/sotu/sotu.ner");
		
//		corpus = Corpus.loadXY("/d/acl/just_meta.xy");
//		corpus.runTokenizer(NLP::simpleTokenize);
//		corpus.runTokenizer(NLP::stanfordTokenize);
//		corpus.loadNLP("/d/acl/just_meta.ner");
		
//		corpus.loadNLP("/d/acl/alltext.pos");
//		corpus = Corpus.loadXY("/d/twi/geo2/data/v8/smalltweets2.smallsample.xy");
//		corpus = Corpus.loadXY("/d/twi/geo2/data/v8/medsamp.xy");
		
//		DocAnalyzer da = new PreAnalysis.UnigramAnalyzer();
		
		NLP.NgramAnalyzer da = new NLP.NgramAnalyzer();
		da.order = 5;
		da.posnerFilter = true;
		
//		NLP.UnigramAnalyzer da = new NLP.UnigramAnalyzer();
		
		for (Document doc : corpus.docsById.values()) {
			NLP.analyzeDocument(da, doc);	
		}
		
		corpus.finalizeIndexing();
	}
	
	double getTermProbThresh() {
		return Double.parseDouble((String) termProbThreshSpinner.getValue());
	}
	int getTermCountThresh() {
//		return 1;
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
		
		int effectiveTermcountThresh = (int) Math.floor(getTermProbThresh() * curDS.terms.totalCount);
		
//		termcountInfo.setText(effectiveTermcountThresh==0 ? "all terms" : U.sf("count >= %d", effectiveTermcountThresh));
		
//		U.p("=== TOP WORDS ===");
//		for (WeightedTerm t : topTerms) {
//			U.pf("%-15s || %d vs %d || %.4g\n", 
//					t.term, 
//					(int) curDS.terms.value(t.term), (int) corpus.globalTerms.value(t.term),
//					t.weight);
//		}

	}
	
	void refreshQueryInfo() {
		String s = U.sf("Current selection: %s docs, %s wordtoks\n", 
				GUtil.commaize(curDS.docs().size()), 
				GUtil.commaize((int)curDS.terms.totalCount));
		queryInfo.setText(s);
	}
	
	class TermTableModel extends AbstractTableModel {
		public TermTableModel() {
		}

		@Override
		public String getColumnName(int j) {
			return (new String[]{ "term", "local","","global", "lift" })[ j ];
		}
		@Override
		public int getRowCount() {
			return focusTerms.size();
		}

		@Override
		public int getColumnCount() {
			return 5;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			WeightedTerm t = focusTerms.get(rowIndex);
			switch (columnIndex) {
			case 0: return t.term;
//			case 1: return U.sf("%.0f : %.0f", curDS.terms.value(t.term), corpus.globalTerms.value(t.term));
			case 1: return U.sf("%d", (int) curDS.terms.value(t.term));
			case 2: return ":";
			case 3: return U.sf("%d", (int) corpus.globalTerms.value(t.term));
			case 4: return t.weight > 1 ? U.sf("%.2f", t.weight) : U.sf("%.4g", t.weight);
			}
			assert false; return null;
		}
		
	}
	void setupTermTable() {
		DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
		centerRenderer.setHorizontalAlignment( JLabel.CENTER );
		DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
		rightRenderer.setHorizontalAlignment( JLabel.RIGHT );

        TableColumn cc;
        cc = tt.table.getColumnModel().getColumn(0);
        cc.setMinWidth(100);
        cc = tt.table.getColumnModel().getColumn(1); cc.setMinWidth(20); cc.setWidth(20);
        cc.setCellRenderer(centerRenderer);
        cc = tt.table.getColumnModel().getColumn(2); cc.setMinWidth(8); cc.setMaxWidth(8);
        cc = tt.table.getColumnModel().getColumn(3); cc.setMinWidth(20); cc.setWidth(20);
        cc.setCellRenderer(centerRenderer);
        cc = tt.table.getColumnModel().getColumn(4);
        cc.setCellRenderer(centerRenderer);
        cc.setMinWidth(50);
        
        tt.table.setAutoCreateRowSorter(true);
        
        tt.table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent e) {
				TermQuery tq = new TermQuery(corpus);
				for (int row : tt.table.getSelectedRows()) {
					String term = (String) tt.table.getValueAt(row,0);
					tq.terms.add(term);
				}
				
				if (tq.terms.size() > 0) {
					subqueryInfo.setText(tq.terms.size()+" selected terms: " + StringUtils.join(tq.terms, ", "));
					textPanel.show(tq.terms, curDS);
					brushPanel.showTerms(tq);
				}
			}
        });
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
	
	void goUI() {
		
        JFrame frame = new JFrame("Text Explorer Tool");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        
        JPanel bppanel = new JPanel();
        bppanel.setLayout(new BoxLayout(bppanel,BoxLayout.Y_AXIS));
        
        queryInfo = new JLabel();
        queryInfo.setPreferredSize(new Dimension(300,20));
        bppanel.add(queryInfo);
        subqueryInfo = new JLabel();
        subqueryInfo.setPreferredSize(new Dimension(300,20));
        bppanel.add(subqueryInfo);
        
        brushPanel = new BrushPanel(this, corpus.allDocs());
        brushPanel.setOpaque(true);
        brushPanel.setBackground(Color.white);
        brushPanel.setMySize(500,300);
        brushPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        brushPanel.setDefaultXYLim(corpus);
        bppanel.add(brushPanel);
        
        JPanel termpanel = new JPanel();
        termpanel.setLayout(new FlowLayout());
        termProbThreshSpinner = new JSpinner(new MySM());
        termProbThreshSpinner.setValue(".0005");
        termProbThreshSpinner.setPreferredSize(new Dimension(100,30));
        termProbThreshSpinner.addChangeListener(e -> refreshTermList());

        termCountThreshSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 1000, 1));
        termCountThreshSpinner.setPreferredSize(new Dimension(60,30));
        termCountThreshSpinner.setValue(1);
        termCountThreshSpinner.addChangeListener(e -> refreshTermList());

        JPanel termprobPanel = new JPanel();
        termprobPanel.setLayout(new BoxLayout(termprobPanel, BoxLayout.X_AXIS));
        termprobPanel.add(new JLabel("Term prob >="));
        termprobPanel.add(termProbThreshSpinner);
        termpanel.add(termprobPanel);
        
//        termcountInfo = new JLabel("");
//        termcountInfo.setMinimumSize(new Dimension(250,30));
//        termprobPanel.add(termcountInfo);
        
        termprobPanel.add(new JLabel("Term count >="));
        termprobPanel.add(termCountThreshSpinner);
        termlistInfo = new JLabel();
        termpanel.add(termlistInfo);
        
        ttModel = new TermTableModel();
        tt = new TermTable(ttModel);
        setupTermTable();
        
        termpanel.setPreferredSize(new Dimension(350,600));
        tt.scrollpane.setPreferredSize(new Dimension(300,500));
        termpanel.add(tt.scrollpane);
        
        textPanel = new TextPanel();
        textPanel.scrollpane.setPreferredSize(new Dimension(400,300));
        bppanel.add(textPanel.scrollpane);
        
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
	
	public static void main(String[] args) throws IOException {
		final Main main = new Main();
		main.initData();
		SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                main.goUI();
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
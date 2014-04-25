package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JFormattedTextField.AbstractFormatter;
import javax.swing.JFormattedTextField.AbstractFormatterFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import org.codehaus.jackson.JsonProcessingException;
import org.jdesktop.swingx.MultiSplitLayout;
import org.jdesktop.swingx.MultiSplitPane;

import util.BasicFileIO;
import util.JsonUtil;
import util.U;
import d.Analysis.TermvecComparison;
import d.Corpus;
import d.DocSet;
import d.Document;
import d.Levels;
import d.NLP;
import d.TermQuery;
import edu.stanford.nlp.util.StringUtils;

interface QueryReceiver {
	public void receiveQuery(Collection<String> docids);
}

@SuppressWarnings("serial")
public class Main implements QueryReceiver {
	public Corpus corpus;
	public DocSet curDS = new DocSet();

	public List<String> docdrivenTerms = new ArrayList<>();
	public List<String> pinnedTerms = new ArrayList<>();
	public List<String> termdrivenTerms = new ArrayList<>();
	
	TermvecComparison fcView;
	
	JFrame mainFrame;
	TermTable  docdrivenTermTable;
	TermTable pinnedTermTable;
	TermTable  termdrivenTermTable;
	BrushPanel brushPanel;
	TextPanel textPanel;
	JLabel queryInfo;
	JLabel subqueryInfo;
	JLabel termlistInfo;
	JSpinner tpSpinner;
	JSpinner tcSpinner;
	JLabel tcInfo;
	
	public void initData() throws JsonProcessingException, IOException {
//		pinnedTerms.add("crime");
//		pinnedTerms.add("soviet");
		corpus = Corpus.loadXY("/d/sotu/sotu.xy");
		corpus.loadNLP("/d/sotu/sotu.ner");
		corpus.yLevels = new Levels();
		corpus.yLevels.loadJSON(JsonUtil.readJsonNX( BasicFileIO.readFile("/d/sotu/schema.json")));
		
//		corpus = Corpus.loadXY("/d/acl/just_meta.xy");
////		corpus.runTokenizer(NLP::simpleTokenize);
////		corpus.runTokenizer(NLP::stanfordTokenize);
//		corpus.loadNLP("/d/acl/just_meta.ner");
//		corpus.yLevels = new Levels();
//		corpus.yLevels.loadJSON(JsonUtil.readJsonNX( BasicFileIO.readFile("/d/acl/schema.json")));

		NLP.NgramAnalyzer da = new NLP.NgramAnalyzer();
		da.order = 5;
		da.posnerFilter = true;

//		corpus = Corpus.loadXY("/d/twi/geo2/data/v8/medsamp.xy");
////		corpus = Corpus.loadXY("/d/twi/geo2/data/v8/smalltweets2.sample.xy");
//		corpus.runTokenizer(NLP::simpleTokenize);
//		NLP.DocAnalyzer da = new NLP.UnigramAnalyzer();
////		NLP.NgramAnalyzer da = new NLP.NgramAnalyzer();
////		da.order = 2;
////		da.posnerFilter = false;
//		
		for (Document doc : corpus.docsById.values()) {
			NLP.analyzeDocument(da, doc);	
		}
		corpus.finalizeIndexing();
	}

	void uiOverrides() {
      brushPanel.minUserY = -2;
      brushPanel.maxUserY = -brushPanel.minUserY;
	}
	

	double getTermProbThresh() {
		return (double) tpSpinner.getValue();
//		return Double.parseDouble((String) termProbThreshSpinner.getValue());
	}
	int getTermCountThresh() {
//		return 1;
		return (int) tcSpinner.getValue();
	}
	
	@Override
	public void receiveQuery(Collection<String> docids) {
		curDS = corpus.getDocSet(docids);
		refreshQueryInfo();
		refreshTermList();
	}
	
	void refreshTermList() {
		fcView = new TermvecComparison(curDS.terms, corpus.globalTerms);
		docdrivenTerms.clear();
		docdrivenTerms.addAll( fcView.topEpmi(getTermProbThresh(), getTermCountThresh()) );
		docdrivenTermTable.model.fireTableDataChanged();
		termlistInfo.setText(U.sf("%d/%d terms", docdrivenTerms.size(), curDS.terms.support().size()));
//		pinnedTermTable.model.fireTableDataChanged();
		pinnedTermTable.updateCalculations();
		
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
		String s = U.sf("Current docvar selection: %s docs, %s wordtoks\n", 
				GUtil.commaize(curDS.docs().size()), 
				GUtil.commaize((int)curDS.terms.totalCount));
		queryInfo.setText(s);
	}
	
	/** this feels race condition-y */
	TermQuery getCurrentTQ() {
		TermQuery curTQ;
    	curTQ = new TermQuery(corpus);
    	for (int row : docdrivenTermTable.table.getSelectedRows()) {
    		curTQ.terms.add(docdrivenTermTable.getTermAt(row));
    	}
    	for (int row : pinnedTermTable.table.getSelectedRows()) {
    		curTQ.terms.add(pinnedTermTable.getTermAt(row));
    	}
    	for (int row : docdrivenTermTable.table.getSelectedRows()) {
    		curTQ.terms.add(docdrivenTermTable.getTermAt(row));
    	}
    	return curTQ;
	}
	
	void runTermQuery() {
		TermQuery curTQ = getCurrentTQ();
		String msg = curTQ.terms.size()==0 ? "No selected terms" 
				: curTQ.terms.size()+" selected terms: " + StringUtils.join(curTQ.terms, ", ");
		subqueryInfo.setText(msg);
		textPanel.show(curTQ.terms, curDS);
		brushPanel.showTerms(curTQ);
	}
	
	
	/** give this a termlist. it consults the global fcView for the terms' stats. */
	public class TermTableModel extends AbstractTableModel {
		List<String> terms;
		public TermTableModel(List<String> terms) {
			this.terms = terms;
		}
		@Override
		public String getColumnName(int j) {
			return (new String[]{ "term", "local","","global", "lift" })[ j ];
		}
		@Override
		public int getRowCount() {
			return terms.size();
		}
		@Override
		public int getColumnCount() {
			return 5;
		}
		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			String t = terms.get(rowIndex);
			double epmi = fcView != null ? fcView.epmi(t) : 0;
			switch (columnIndex) {
			case 0: return t;
//			case 1: return U.sf("%.0f : %.0f", curDS.terms.value(t.term), corpus.globalTerms.value(t.term));
			
//			case 1: return U.sf("%d", (int) curDS.terms.value(t));
			case 1: return (int) curDS.terms.value(t);
			case 2: return ":";
//			case 3: return U.sf("%d", (int) corpus.globalTerms.value(t));
			case 3: return (int) corpus.globalTerms.value(t);
//			case 4: return epmi > 1 ? U.sf("%.2f", epmi) : U.sf("%.4g", epmi);
//			case 4: return epmi > .001 ? U.sf("%.3f", epmi) : U.sf("%.4g", epmi);
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
	
	void setupTermTable(final TermTable tt) {
		tt.setupTermTable();

        tt.table.getSelectionModel().addListSelectionListener(e -> {
        	if (!e.getValueIsAdjusting()) {
        		runTermQuery(); 
    		}});
	}
	
	void pinTerm(String term) { 
		pinnedTerms.add(term);
//		refreshTermList();
//		refreshQueryInfo();
//		pinnedTermTable.model.fireTableRowsInserted(pinnedTerms.size()-2, pinnedTerms.size()-1);
		pinnedTermTable.model.fireTableRowsInserted(0, pinnedTerms.size()-1);
	}
	void unpinTerm(String term) {
//		refreshTermList();
//		refreshQueryInfo();
		int[] rowsToDel = IntStream.range(0,pinnedTermTable.model.getRowCount())
			.filter(row -> pinnedTermTable.getTermAt(row).equals(term))
			.toArray();
		for (int row : rowsToDel) {
			pinnedTermTable.model.fireTableRowsDeleted(row,row);	
		}
		pinnedTerms.remove(term);
	}
	
	static JPanel titledPanel(String title, JComponent internal) {
		JPanel top = new JPanel();
		top.add(new JLabel(title));
		top.add(internal);
		return top;
	}

	void setupUI() {
        int leftwidth = 365-5, rightwidth=430-5, height=550;

        mainFrame = new JFrame("Text Explorer Tool");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        
        /////////////////  termpanel  ///////////////////
        
//        JPanel termpanel = new JPanel();
//        termpanel.setLayout(new FlowLayout());
//        termpanel.setPreferredSize(new Dimension(leftwidth,height));
//        
        String layoutDef = "(COLUMN pinned termfilter docdriven termdriven)"; 
        MultiSplitLayout.Node modelRoot = MultiSplitLayout.parseModel(layoutDef);
        MultiSplitPane termpanel = new MultiSplitPane();
        termpanel.setDividerSize(5);
        termpanel.getMultiSplitLayout().setModel(modelRoot);
        termpanel.setPreferredSize(new Dimension(leftwidth,height));

        setupTermfilterSpinners();

        JPanel termfilterPanel = new JPanel();
        termfilterPanel.setLayout(new BoxLayout(termfilterPanel, BoxLayout.X_AXIS));
        termfilterPanel.add(new JLabel("Term Prob >="));
        termfilterPanel.add(tpSpinner);
        termpanel.add(termfilterPanel, "termfilter");
        
        termfilterPanel.add(new JLabel("Count >="));
        termfilterPanel.add(tcSpinner);
        
        termlistInfo = new JLabel();
//        termlistInfo.setPreferredSize(new Dimension(leftwidth,20));
//        termpanel.add(termlistInfo, "termlistinfo");
        
        //////  termtable: below the frequency spinners  /////
        
        docdrivenTermTable = new TermTable(new TermTableModel(docdrivenTerms));
        setupTermTable(docdrivenTermTable);
        docdrivenTermTable.doubleClickListener = this::pinTerm;
        
        termdrivenTermTable = new TermTable(new TermTableModel(termdrivenTerms));
        termdrivenTermTable.setupTermTable();
        
        pinnedTermTable = new TermTable(new TermTableModel(pinnedTerms));
        setupTermTable(pinnedTermTable);
        pinnedTermTable.doubleClickListener = this::unpinTerm;
        
        JPanel pinnedWrapper = new JPanel(new BorderLayout());
        pinnedWrapper.add(new JLabel("Pinned terms"), BorderLayout.NORTH);
        pinnedWrapper.add(pinnedTermTable.top(), BorderLayout.CENTER);
        
//        JPanel docdrivenWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPanel docdrivenWrapper = new JPanel(new BorderLayout());
        JPanel topstuff = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topstuff.add(new JLabel("Docvar-associated terms"));
        topstuff.add(termlistInfo);
//        docdrivenWrapper.add(new JLabel("Docvar-associated terms"), BorderLayout.NORTH);
//        docdrivenWrapper.add(termlistInfo, BorderLayout.NORTH);
        docdrivenWrapper.add(topstuff, BorderLayout.NORTH);
        docdrivenWrapper.add(docdrivenTermTable.top(), BorderLayout.CENTER);
        
        JPanel termdrivenWrapper = new JPanel(new BorderLayout());
        termdrivenWrapper.add(new JLabel("Term-associated terms"), BorderLayout.NORTH);
        termdrivenWrapper.add(termdrivenTermTable.top(), BorderLayout.CENTER);
        
//        docdrivenWrapper.setPreferredSize(new Dimension(leftwidth-10,150));
        termpanel.add(docdrivenWrapper, "docdriven");
        pinnedWrapper.setPreferredSize(new Dimension(-1, 200));
        termpanel.add(pinnedWrapper, "pinned");
//        termdrivenWrapper.setPreferredSize(new Dimension(leftwidth-10,150));
        termpanel.add(termdrivenWrapper, "termdriven");
        
        //////////////////////////  brush panel  /////////////////////////
        
        JPanel bppanel = new JPanel();
        bppanel.setPreferredSize(new Dimension(rightwidth,height));
        bppanel.setLayout(new BoxLayout(bppanel,BoxLayout.Y_AXIS));

        queryInfo = new JLabel();
        queryInfo.setPreferredSize(new Dimension(300,20));
        bppanel.add(queryInfo);
        subqueryInfo = new JLabel();
        subqueryInfo.setPreferredSize(new Dimension(300,20));
        bppanel.add(subqueryInfo);
        
        brushPanel = new BrushPanel(this, corpus.allDocs());
        brushPanel.yLevels = corpus.yLevels;
        brushPanel.setOpaque(true);
        brushPanel.setBackground(Color.white);
        brushPanel.setMySize(rightwidth,250);
        brushPanel.setBorder(BorderFactory.createLineBorder(Color.black));
        brushPanel.setDefaultXYLim(corpus);
        bppanel.add(brushPanel);
        
        textPanel = new TextPanel();
        textPanel.scrollpane.setPreferredSize(new Dimension(rightwidth,300));
        bppanel.add(textPanel.scrollpane);
        
        mainFrame.setLayout(new FlowLayout());
        mainFrame.setSize(leftwidth+rightwidth, height);
        mainFrame.getContentPane().add(termpanel);
        mainFrame.getContentPane().add(bppanel);
        mainFrame.pack();
	}

	void setupTermfilterSpinners() {
		tpSpinner = new JSpinner(new SpinnerStuff.MySM());
        JFormattedTextField tpText = ((JSpinner.DefaultEditor) tpSpinner.getEditor()).getTextField();
        tpText.setFormatterFactory(new AbstractFormatterFactory() {
			@Override public AbstractFormatter getFormatter(JFormattedTextField tf) {
//				return new SpinnerStuff.SimpleFractionFormatter();
				return new SpinnerStuff.NiceFractionFormatter();
			}
        });
        tpSpinner.setValue(.0005);
        tpSpinner.setPreferredSize(new Dimension(150,30));
        tpSpinner.addChangeListener(e -> refreshTermList());

        tcSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 1000, 1));
        tcSpinner.setPreferredSize(new Dimension(60,30));
        tcSpinner.setValue(1);
        tcSpinner.addChangeListener(e -> refreshTermList());
	}
	
	public static void main(String[] args) throws IOException {
		final Main main = new Main();
		main.initData();
		SwingUtilities.invokeLater(() -> {
			main.setupUI();
			main.uiOverrides();
			main.mainFrame.setVisible(true);
		});
	}
}

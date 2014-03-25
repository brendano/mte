package ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFormattedTextField;
import javax.swing.JFormattedTextField.AbstractFormatter;
import javax.swing.JFormattedTextField.AbstractFormatterFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

import org.codehaus.jackson.JsonProcessingException;

import util.BasicFileIO;
import util.JsonUtil;
import util.U;
import d.Analysis;
import d.Analysis.FocusContrastView;
import d.Corpus;
import d.DocSet;
import d.Document;
import d.Levels;
import d.NLP;
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

//	TermQuery curTQ;
	public List<String> focusTerms = new ArrayList<>();
	public List<String> pinnedTerms = new ArrayList<>();
	FocusContrastView fcView;
	
	JFrame mainFrame;
	TermTable  focusTermTable;
	TermTable pinnedTermTable;
	BrushPanel brushPanel;
	TextPanel textPanel;
	JLabel queryInfo;
	JLabel subqueryInfo;
	JLabel termlistInfo;
	JSpinner tpSpinner;
	JSpinner tcSpinner;
	JLabel tcInfo;
	
	void initData() throws JsonProcessingException, IOException {
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

//		corpus = Corpus.loadXY("/d/twi/geo2/data/v8/smalltweets2.smallsample.xy");
//		corpus = Corpus.loadXY("/d/twi/geo2/data/v8/medsamp.xy");
//		corpus.runTokenizer(NLP::simpleTokenize);
//		DocAnalyzer da = new NLP.UnigramAnalyzer();
		
		for (Document doc : corpus.docsById.values()) {
			NLP.analyzeDocument(da, doc);	
		}
		corpus.finalizeIndexing();
	}

	void uiOverrides() {
//      brushPanel.minUserY = -1.5;
//      brushPanel.maxUserY = 1.5;
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
		fcView = new FocusContrastView(curDS.terms, corpus.globalTerms);
		focusTerms.clear();
		focusTerms.addAll( fcView.topEpmi(getTermProbThresh(), getTermCountThresh()) );
		focusTermTable.model.fireTableDataChanged();
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
	
	/** this feels race condition-y */
	TermQuery getCurrentTQ() {
		TermQuery curTQ;
    	curTQ = new TermQuery(corpus);
    	for (int row : focusTermTable.table.getSelectedRows()) {
    		String term = (String) focusTermTable.table.getValueAt(row,0);
    		curTQ.terms.add(term);
    	}
    	for (String w : pinnedTerms) {
    		curTQ.terms.add(w);
    	}
    	return curTQ;
	}
	
	void updateAndRunTermQuery() {
		TermQuery curTQ = getCurrentTQ();
    	if (curTQ.terms.size() > 0) {
    		subqueryInfo.setText(curTQ.terms.size()+" selected terms: " + StringUtils.join(curTQ.terms, ", "));
    		textPanel.show(curTQ.terms, curDS);
    		brushPanel.showTerms(curTQ);
    	}
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
			double epmi = fcView.epmi(t);
			switch (columnIndex) {
			case 0: return t;
//			case 1: return U.sf("%.0f : %.0f", curDS.terms.value(t.term), corpus.globalTerms.value(t.term));
			case 1: return U.sf("%d", (int) curDS.terms.value(t));
			case 2: return ":";
			case 3: return U.sf("%d", (int) corpus.globalTerms.value(t));
			case 4: return epmi > 1 ? U.sf("%.2f", epmi) : U.sf("%.4g", epmi);
			}
			assert false; return null;
		}
		
	}
	
	void setupTermTable(TermTable tt) {
		tt.table.setFillsViewportHeight(true);
		
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

        tt.table.getSelectionModel().addListSelectionListener(e -> updateAndRunTermQuery());
	}
	

	void setupUI() {
        int leftwidth = 400, rightwidth=400, height=600;

        mainFrame = new JFrame("Text Explorer Tool");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        
        /////////////////  termpanel  ///////////////////
        
        JPanel termpanel = new JPanel();
        termpanel.setLayout(new FlowLayout());
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

        JPanel termprobPanel = new JPanel();
        termprobPanel.setLayout(new BoxLayout(termprobPanel, BoxLayout.X_AXIS));
        termprobPanel.add(new JLabel("Term Prob >="));
        termprobPanel.add(tpSpinner);
        termpanel.add(termprobPanel);
        
//        tcInfo = new JLabel("");
//        tcInfo.setMinimumSize(new Dimension(250,30));
//        termprobPanel.add(tcInfo);
        
        termprobPanel.add(new JLabel("Count >="));
        termprobPanel.add(tcSpinner);
        termlistInfo = new JLabel();
        termlistInfo.setPreferredSize(new Dimension(leftwidth,20));
        termpanel.add(termlistInfo);
        
        //////  termtable: below the frequency spinners  /////
        
        focusTermTable = new TermTable(new TermTableModel(focusTerms));
        setupTermTable(focusTermTable);
        
        termpanel.setPreferredSize(new Dimension(leftwidth,height));
        focusTermTable.scrollpane.setPreferredSize(new Dimension(leftwidth,height-120));
        termpanel.add(focusTermTable.scrollpane);

        
        //////////////////////////  brush panel  /////////////////////////
        
        JPanel bppanel = new JPanel();
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
        brushPanel.setMySize(rightwidth,300);
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

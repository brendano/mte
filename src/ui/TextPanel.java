package ui;

import java.util.*;

import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.event.ListSelectionListener;

import com.google.common.base.Function;
import com.google.common.collect.Ordering;

import util.U;
import util.misc.Pair;

import d.Corpus;
import d.DocSet;
import d.Document;
import edu.stanford.nlp.util.Sets;

public class TextPanel  {
//	JTextPane area;
	JEditorPane area;
	JScrollPane scrollpane;
	
	public int wordRadius = 6;
	
	public TextPanel() {
//		area = new JTextArea();
		area = new JEditorPane("text/html","");
        area.setEditable(false);
        area.setText("");
        scrollpane = new JScrollPane(area);
	}
	
	public void show(Collection<String> terms, DocSet docs) {
		StringBuilder s = new StringBuilder();
		
		List<Document> doclist = new ArrayList<>(docs.docs());
		Collections.sort(doclist, Ordering.natural().onResultOf(d -> d.docid));
		Set<String> termset = new HashSet<>(terms);
		
		for (Document d : doclist) {
			if ( ! Sets.intersects(d.termVec.support(), termset)) {
				continue;
			}
			s.append(U.sf("%s\n", d.docid));
			s.append(passageReport(d, termset));
			s.append("\n");
		}
		String finalstr = s.toString().replace("\n","<br>");
		area.setText(finalstr);
//		scrollpane.getVerticalScrollBar().setValue(0);
	}
	
	
	StringBuilder passageReport(Document d, Set<String> terms) {
		StringBuilder s = new StringBuilder();
		
		List< Pair<String, List<Integer>>> hits = new ArrayList<>();
		
		for (int i=0; i<d.tokens.size(); i++) {
			String w = d.tokens.get(i).text.toLowerCase();
			if (terms.contains(w)) {
				List<Integer> tokindsHere = new ArrayList<>();
				for (int j= Math.max(i-wordRadius, 0); j<Math.min(i+wordRadius, d.tokens.size()); j++) {
					tokindsHere.add(j);
				}
				hits.add(U.pair(w, tokindsHere));
			}
		}
		
		Collections.sort(hits, Ordering.natural().onResultOf(p -> p.second.get(0)));
		
		for (Pair<String,List<Integer>> hit : hits) {
			s.append("&nbsp; -");
			for (int j : hit.second) {
				s.append(" ");
				String w = d.tokens.get(j).text;
				if (w.toLowerCase().equals(hit.first)) {
//						s.append("**" + d.tokens.get(j).text + "**");
					s.append("<b>" + w + "</b>");
				}
				else {
					s.append(w);
				}
			}
			s.append("\n");
		}
		return s;
	}
}

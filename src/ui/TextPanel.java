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
import d.TermInstance;
import edu.stanford.nlp.util.Sets;

public class TextPanel  {
//	JTextPane area;
	JEditorPane area;
	JScrollPane scrollpane;
	
	public int wordRadius = 5;

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
	
	static class WithinDocHit {
		// [inclusive,exclusive)
		int spanStart, spanEnd;
		int termStart, termEnd;
	}
	
	StringBuilder passageReport(Document d, Set<String> terms) {
		List<WithinDocHit> hits = getHitsInDoc(d, terms, 10);
		return makeHTML(d, hits);
	}

	StringBuilder makeHTML(Document d, List<WithinDocHit> hits) {
		StringBuilder s = new StringBuilder();
		for (WithinDocHit h : hits) {
			assert h.spanStart<=h.termStart && h.termStart<=h.termEnd && h.termEnd <= h.spanEnd;
			s.append("&nbsp; -");
			for (int j=h.spanStart; j<h.spanEnd; j++) {
				s.append(" ");
				if (j==h.termStart) s.append("<b>");
				String w = d.tokens.get(j).text;
				w = htmlEscape(w);
				s.append(w);
				if (j+1==h.termEnd) s.append("</b>"); 
			}
			s.append("\n");
		}
//		U.p("END");
		return s;
	}

	List<WithinDocHit> getHitsInDoc(Document d, Set<String> terms, int maxHitsWithinDoc) {
		List<WithinDocHit> hits = new ArrayList<>();
		for (int i=0; i<d.tokens.size(); i++) {
			if ( ! d.tisByStartTokindex.containsKey(i)) continue;
			for (TermInstance ti : d.tisByStartTokindex.get(i)) {
				if (terms.contains(ti.termName)) {
					WithinDocHit h = new WithinDocHit();
					h.termStart = ti.tokIndsInDoc.get(0);
					h.termEnd = ti.tokIndsInDoc.get( ti.tokIndsInDoc.size()-1 ) + 1;
					h.spanStart = Math.max(h.termStart-wordRadius, 0);
					h.spanEnd = Math.min(h.termEnd+wordRadius, d.tokens.size()); 
					hits.add(h);
				}
				if (hits.size() >= maxHitsWithinDoc) break;
			}
			if (hits.size() >= maxHitsWithinDoc) break;
		}
		
		Collections.sort(hits, Ordering.natural().onResultOf(h -> U.pair(h.spanStart, h.termStart)));
		return hits;
	}
	static String htmlEscape(String s) {
		return s.replace("<","&lt;").replace(">","&gt;").replace("&","&amp;");
	}
}

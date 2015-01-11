package te.ui.textview;

import java.util.*;
import java.util.function.Function;

import te.data.Document;
import te.data.TermInstance;
import util.U;

public class Highlighter {
	
	public static String idForTermInstance(TermInstance ti) {
		return "tokind_" + ti.tokIndsInDoc.get(0);
	}
	
	public static String escapeHTML(char c) {
		if (c=='&') return "&amp;";
		if (c=='<') return "&lt;";
		if (c=='>') return "&gt;";
		return Character.toString(c);
	}
	static String highlightTermsAsHTML(Collection<String> terms, Document doc) {
		StringBuilder output = new StringBuilder();
		Set<TermInstance> currentlyInsideThese = new HashSet<>();
		
		for (int cur=0; cur < doc.text.length(); cur++) {
			int next = cur+1;

			for (TermInstance ti : doc.tisByStartCharindex.getOrDefault(cur, Collections.EMPTY_LIST)) {
				output.append("<a name=\"" + idForTermInstance(ti) + "\">" );
				if (terms.contains(ti.termName)) {
					currentlyInsideThese.add(ti);
					output.append("<b>");	
				}
				
			}
//			output.append(transformer.apply(doc.text.charAt(cur));
			char c = doc.text.charAt(cur);
			if (c=='&' || c=='<' || c=='>') {
				output.append(escapeHTML(c));
			} else {
				output.append(c);
			}
			
			for (TermInstance ti : doc.tisByEndCharindex.getOrDefault(next, Collections.EMPTY_LIST)) {
				output.append("</a>");
				if (currentlyInsideThese.contains(ti)) {
					currentlyInsideThese.remove(ti);
					output.append("</b>");	
				}
			}
		}
		return output.toString();
		
	}

}

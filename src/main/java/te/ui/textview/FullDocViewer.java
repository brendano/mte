package te.ui.textview;

import java.util.Collection;

import javax.swing.JComponent;

import te.data.Document;
import te.ui.queries.AllQueries;
import util.Timer;

public class FullDocViewer {
//	public JEditorPane htmlpane;
	public MyTextArea textarea;
	
	public FullDocViewer() {
		textarea = new MyTextArea();
	}

	public JComponent top() { return textarea.top(); }

	Timer timer = new Timer();
	
	public void show(Collection<String> terms, Document doc) {
//		U.p("show doc " + doc);
		boolean isNewDoc = textarea.doc!=doc;
		textarea.setDocument(doc);
		showForCurrentDoc(terms, isNewDoc);
	}

	public void showForCurrentDoc(Collection<String> terms, boolean isNewDoc) {
		textarea.termHighlighter = ti -> terms.contains(ti.termName) ? 
				AllQueries.foregroundVersion(AllQueries.instance().termQueryColor) 
				: null;
		if (isNewDoc) {
			textarea.launchTextRender(isNewDoc);
		}
	}
	
	static String escapeHTML(String input) {
		// one of the apache commons libraries has a better function. but have to check what the swing component actually understands.
		// it advertises itself as html 3.2 compatible.. yikes.
		String newstr = input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return newstr;
	}

}

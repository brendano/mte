package te.ui.textview;

import java.awt.Color;
import java.util.Collection;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.text.DefaultCaret;

import te.data.DocSet;
import te.data.Document;
import util.U;

public class FullDocViewer {
	public JEditorPane htmlpane;
	public JScrollPane scrollpane;
	Document currentDoc;  // could possibly be null if no doc is selected
	
	public FullDocViewer() {
		htmlpane = new JEditorPane();
		htmlpane.setContentType( "text/html" );
		htmlpane.setEditable(false);
//		htmlpane.setBackground(Color.white);
//		htmlpane.setLayout(new BoxLayout(htmlpane,BoxLayout.Y_AXIS));

		DefaultCaret caret = (DefaultCaret)htmlpane.getCaret();
		caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);

        scrollpane = new JScrollPane(htmlpane);
        scrollpane.setViewportView(htmlpane);
	}

	public JComponent top() { return scrollpane; }

	public void show(Collection<String> terms, Document doc) {
		currentDoc = doc;
		showForCurrentDoc(terms);
	}
	
	public void showForCurrentDoc(Collection<String> terms) {
		if (currentDoc==null) return;
		String newstr;
		String smark = "_AAASTARTBBB_";
		String emark = "_AAAENDBBB_";
		newstr = Highlighter.highlightTermsAsHTML(terms, currentDoc, 
				smark,emark );
		U.p(newstr.substring(0,1000));
//		newstr = escapeHTML(newstr);
//		newstr = newstr.replace(smark, "<b>");
//		newstr = newstr.replace(emark, "</b>");
		newstr = newstr.replace("\\r\\n", "\\n");
		newstr = newstr.replace("\\r", "\\n");
		newstr = newstr.replaceAll("(\\n\\s*?)(\\n[ \\t]*)+", "\n<br><br>\n"); // two newlines gets a break
//		newstr = newstr.replace("\n", "<br>");
//		newstr = newstr.replace("\n\n", "<br><br>");
//		newstr = newstr.replace("\r\n\r\n", "<br><br>");
//		newstr = newstr.replace("\r\r", "<br><br>");
		htmlpane.setText(newstr);
		scrollpane.getVerticalScrollBar().setValue(0);
	}
	
	static String escapeHTML(String input) {
		// one of the apache commons libraries has a better function. but have to check what the swing component actually understands.
		// it advertises itself as html 3.2 compatible.. yikes.
		String newstr = input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return newstr;
	}

}

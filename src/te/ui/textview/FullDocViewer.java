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
import util.Timer;
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

	Timer timer = new Timer();
	
	public void show(Collection<String> terms, Document doc) {
		boolean newdoc = doc==currentDoc;
		currentDoc = doc;
		showForCurrentDoc(terms, newdoc);
		timer.report();
	}

	public void showForCurrentDoc(Collection<String> terms, boolean isNewDoc) {
		if (currentDoc==null) return;
		String newstr;
		timer.tick("create html");
		newstr = Highlighter.highlightTermsAsHTML(terms, currentDoc);
		timer.tock();

		timer.tick("string sub");
		newstr = newstr.replace("\\r\\n", "\\n");
		newstr = newstr.replace("\\r", "\\n");
		newstr = newstr.replaceAll("(\\n[ \\t]*)(\\n[ \\t]*)+", "\n<br><br>\n"); // two or more newlines gets a double-br break
//		newstr = newstr.replace("\n", "<br>");
//		newstr = newstr.replace("\n\n", "<br><br>");
//		newstr = newstr.replace("\r\n\r\n", "<br><br>");
//		newstr = newstr.replace("\r\r", "<br><br>");
		timer.tock();
		int curpos = scrollpane.getVerticalScrollBar().getValue();
		timer.tick("set text");
		htmlpane.setText(newstr);
		timer.tock();
		if (isNewDoc) {
			scrollpane.getVerticalScrollBar().setValue(0);	
		}
	}
	
	static String escapeHTML(String input) {
		// one of the apache commons libraries has a better function. but have to check what the swing component actually understands.
		// it advertises itself as html 3.2 compatible.. yikes.
		String newstr = input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return newstr;
	}

}

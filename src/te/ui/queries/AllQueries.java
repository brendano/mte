package te.ui.queries;
import java.awt.Color;
import java.util.*;
import java.util.function.Consumer;

import te.data.Corpus;
import te.data.DocSet;
import te.data.TermQuery;
import te.ui.GUtil;
import util.U;

/** intended to be a singleton that encapsulates all selections global in the system right now. */
public class AllQueries {
	public Set<String> docPanelSelectedDocIDs = Collections.emptySet();
	public String fulldocPanelCurrentDocID;
	private TermQuery _termQuery;
	/** necessary for at least some convenience calls.  is this a bad idea to have here? */
	public Corpus corpus;
	
	public Color docPanelQueryColor = GUtil.Dark2[1];
	public Color termQueryColor = GUtil.Dark2[0];
	public Color fulldocColor = Color.BLACK;
	
	public static Color highlightVersion(Color basecolor) {
		int r=basecolor.getRed(), g=basecolor.getGreen(), b=basecolor.getBlue();
		return new Color(r,g,b, 30);
	}
	
	public TermQuery termQuery() {
		if (_termQuery==null) {
			return new TermQuery(corpus);
		}
		return _termQuery;
	}
	public void setTermQuery(TermQuery tq) {
		_termQuery = tq;
	}
	
	public DocSet curDocs() {
		return corpus.getDocSet(docPanelSelectedDocIDs);
	}
	
	private static AllQueries _instance;
	
	public synchronized static AllQueries instance() {
		if (_instance==null) {_instance = new AllQueries(); }
		return _instance;
	}
	
	public String toString() {
		return "\nAQ\n" +
				"bp cov " + docPanelSelectedDocIDs + "\n" +
				"fulldoc " + fulldocPanelCurrentDocID + "\n" +
				"tq " + termQuery() + "\n" +
				"\n";
	}

}

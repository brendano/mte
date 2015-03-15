package te.ui.queries;
import java.util.*;
import java.util.function.Consumer;

import te.data.Corpus;
import te.data.DocSet;
import te.data.TermQuery;
import util.U;

/** intended to be a singleton that encapsulates all selections global in the system right now. */
public class AllQueries {
	public Set<String> docPanelSelectedDocIDs = Collections.emptySet();
	public String fulldocPanelCurrentDocID;
	private TermQuery _termQuery;
	/** necessary for at least some convenience calls.  is this a bad idea to have here? */
	public Corpus corpus;
	
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
	
	public Collection<String> docQueryDocIDs() {
		Set<String> docids = new HashSet<>();
		docids.addAll(docPanelSelectedDocIDs);
		if (fulldocPanelCurrentDocID!=null) {
			docids.add(fulldocPanelCurrentDocID);
		}
		return docids;
	}

	public String toString() {
		return "\nAQ\n" +
				"bp cov " + docPanelSelectedDocIDs + "\n" +
				"fulldoc " + fulldocPanelCurrentDocID + "\n" +
				"tq " + termQuery() + "\n" +
				"\n";
	}

}

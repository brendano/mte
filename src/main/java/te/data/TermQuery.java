package te.data;

import java.util.ArrayList;
import java.util.List;

import util.U;

public class TermQuery {
	public List<String> terms;
	private DocSet matchingDocs = null;
	private Corpus corpus;
	
	public TermQuery(Corpus corpus) {
		terms = new ArrayList<>();
		this.corpus=corpus;
	}
	
	public DocSet getMatchingDocs() {
		if (matchingDocs == null) {
			matchingDocs = corpus.select(terms);
		}
		return matchingDocs;
	}
	
	@Override
	public String toString() {
		return String.format("TQ[%s terms, %s docs]", 
				terms==null?null:terms.size(), 
				matchingDocs==null?null:matchingDocs.docsById.size());
	}
}

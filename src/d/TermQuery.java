package d;

import java.util.ArrayList;
import java.util.List;

import util.U;

public class TermQuery {
	// disjunctive query
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
}

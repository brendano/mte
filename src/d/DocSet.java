package d;

import java.util.*;

public class DocSet {
	public List<Document> docs;
	public TermVector terms;

	public DocSet() {
		init();
	}
	void init() {
		docs = new ArrayList<>();
		terms = new TermVector();
	}
	public DocSet(Collection<Document> _docs) {
		init();
		for (Document d : _docs) {
			add(d);
		}
	}
	
	public void add(Document d) {
		docs.add(d);
		terms.addInPlace(d.termVec);
	}
}

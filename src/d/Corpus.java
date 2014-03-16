package d;

import java.util.*;

public class Corpus {
	public Map<String,Document> docsById;
	public TermVector globalTerms;
	
	public Collection<Document> allDocs() {
		return docsById.values();
	}
	
	public DocSet getDocSet(Collection<String> docids) {
		DocSet ds = new DocSet();
		for (String docid : docids) {
			ds.add( docsById.get(docid) );
		}
		return ds;
	}
	
	public DocSet select(int minX, int maxX, int minY, int maxY) {
		DocSet ds = new DocSet();
		for (Document d : docsById.values()) {
			if (d.x >= minX && d.x <= maxX && d.y>=minY && d.y<=maxY) {
				ds.add(d);	
			}
		}
		return ds;
	}
	public static Corpus load(String filename) {
		Corpus c = new Corpus();
		c.docsById = new HashMap<>();
		for (Document d : Document.load(filename)) {
			assert ! c.docsById.containsKey(d.docid) : "nonunique docid: " + d.docid;
			c.docsById.put(d.docid, d);
		}
		DocSet allds = new DocSet( c.docsById.values() );
		c.globalTerms = allds.terms;
		return c;
	}
	

}

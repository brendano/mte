package d;

import java.io.IOException;
import java.util.*;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;

import util.BasicFileIO;
import util.JsonUtil;

public class Corpus {
	public Map<String,Document> docsById;
	public TermVector globalTerms;
	InvertedIndex index;
	
	private Corpus() {
		docsById = new HashMap<>();
		index = new InvertedIndex();
	}
	
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
	public static Corpus loadXY(String filename) {
		Corpus c = new Corpus();
		for (Document d : Document.loadXY(filename)) {
			assert ! c.docsById.containsKey(d.docid) : "nonunique docid: " + d.docid;
			c.docsById.put(d.docid, d);
		}
		return c;
	}
	public void loadNLP(String filename) throws JsonProcessingException, IOException {
		for (String line : BasicFileIO.openFileLines(filename)) {
			String parts[] = line.split("\t");
			String docid = parts[0];
			if ( ! docsById.containsKey(docid)) continue;
			JsonNode jdoc = JsonUtil.readJson(parts[1]);
			docsById.get(docid).loadFromNLP(jdoc);
		}
	}
	public void finalizeIndexing() {
		for (Document d : docsById.values()) {
			index.add(d);	
		}
		DocSet allds = new DocSet( docsById.values() );
		globalTerms = allds.terms;
	}

	public DocSet select(List<String> terms) {
		DocSet ret = new DocSet();
		for (String term : terms) {
			for (Document d : index.getMatchingDocs(term)) {
				if (d.termVec.value(term) > 0) {
					ret.add(d);
				}
			}
		}
		return ret;
	}

}

package d;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;

import util.BasicFileIO;
import util.JsonUtil;

public class Corpus {
	public Map<String,Document> docsById;
	public TermVector globalTerms;
	InvertedIndex index;
	SpatialIndex hierIndex;
	DoubleSummaryStatistics xSummary, ySummary;
	
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
	
	public DocSet naiveSelect(int minX, int maxX, int minY, int maxY) {
		DocSet ds = new DocSet();
		docsById.values().stream()
			.filter(d ->d.x >= minX && d.x <= maxX && d.y>=minY && d.y<=maxY)
			.forEach(d -> ds.add(d));
		return ds;
	}
	public DocSet select(int minX, int maxX, int minY, int maxY) {
		return naiveSelect(minX, maxX, minY, maxY);
	}
	public static Corpus loadXY(String filename) {
		Corpus c = new Corpus();
		for (Document d : Document.loadXY(filename)) {
			assert ! c.docsById.containsKey(d.docid) : "nonunique docid: " + d.docid;
			c.docsById.put(d.docid, d);
		}
		return c;
	}
	public void runTokenizer(Function<String,List<Token>> tokenizer) {
		for (Document d : docsById.values()) {
			d.tokens = tokenizer.apply(d.text);
		}
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
//		xSummary = docsById.values().stream().mapToDouble(d->d.x).summaryStatistics();
//		ySummary = docsById.values().stream().mapToDouble(d->d.y).summaryStatistics();
//		hierIndex = new HierIndex(16, xSummary.getMin(), xSummary.getMax(), ySummary.getMin(), ySummary.getMax());
//		hierSums.doSpatialSums(docsById.values());
//		hierSums.dump();

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

package d;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;

import util.BasicFileIO;
import util.JsonUtil;
import util.U;

public class Corpus {
	public Map<String,Document> docsById;
	public TermVector globalTerms;
	InvertedIndex index;
	SpatialIndex hierIndex;
	DoubleSummaryStatistics xSummary, ySummary;
	public Levels yLevels;
	
	double doclenSumSq = 0;
	
	private Corpus() {
		docsById = new HashMap<>();
		index = new InvertedIndex();
	}
	
	public void loadLevels(String filename) throws FileNotFoundException {
		yLevels = new Levels();
		yLevels.loadJSON(JsonUtil.readJsonNX( BasicFileIO.readFile(filename) ));
	}
	
	public Collection<Document> allDocs() {
		return docsById.values();
	}
	
	/** sum_d n_d n_dw ... todo, cache here */ 
	public double termSumSq(String term) {
		return index.getMatchingDocs(term).stream().collect(Collectors.summingDouble(
						d -> d.termVec.totalCount * d.termVec.value(term) ));
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
	public void indicatorize() {
		for (Document d : docsById.values()) {
			TermVector newvec = new TermVector();
			for (String w : d.termVec.support()) {
				newvec.increment(w);
			}
			d.termVec = newvec;
		}
	}
	public void finalizeIndexing() {
//		xSummary = docsById.values().stream().mapToDouble(d->d.x).summaryStatistics();
//		ySummary = docsById.values().stream().mapToDouble(d->d.y).summaryStatistics();
//		hierIndex = new HierIndex(16, xSummary.getMin(), xSummary.getMax(), ySummary.getMin(), ySummary.getMax());
//		hierSums.doSpatialSums(docsById.values());
//		hierSums.dump();

		U.p("finalizing");
		for (Document d : docsById.values()) {
			index.add(d);
			double n = d.termVec.totalCount;
			doclenSumSq += n*n;
		}
		DocSet allds = new DocSet( docsById.values() );
		globalTerms = allds.terms;
		U.p("done finalizing");
	}

	/** disjunction query */
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

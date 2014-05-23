package d;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;

import d.Schema.ColumnInfo;
import d.Schema.DataType;
import exceptions.BadData;
import exceptions.BadSchema;
import util.BasicFileIO;
import util.JsonUtil;
import util.U;

public class Corpus {
	public Map<String,Document> docsById;
	List<Document> docsInOriginalOrder;
	public TermVector globalTerms;
	InvertedIndex index;
//	SpatialIndex hierIndex;
//	DoubleSummaryStatistics xSummary, ySummary;
	public Schema schema;
	public Map<String,SummaryStats> covariateSummaries;
	double doclenSumSq = 0;
	public boolean needsCovariateTypeConversion = false;
	
	public Corpus() {
		docsById = new HashMap<>();
		index = new InvertedIndex();
		docsInOriginalOrder = new ArrayList<>();
		schema = new Schema();
	}
	
	public Collection<Document> allDocs() {
//		return docsById.values();
		return docsInOriginalOrder;
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
	
	public DocSet naiveSelect(String xAttr, String yAttr, int minX, int maxX, int minY, int maxY) {
		DocSet ds = new DocSet();
		docsById.values().stream()
			.filter(d -> 
				schema.getDouble(d,xAttr) >= minX && 
				schema.getDouble(d,xAttr) <= maxX &&
				schema.getDouble(d,yAttr) >=minY && 
				schema.getDouble(d,yAttr) <=maxY)
			.forEach(d -> ds.add(d));
		return ds;
	}
	
	public DocSet select(String xAttr, String yAttr, int minX, int maxX, int minY, int maxY) {
		return naiveSelect(xAttr, yAttr, minX, maxX, minY, maxY);
	}
	
	public void loadJson(String filename) throws BadData, IOException {
		for (Document d : Document.loadJson(filename)) {
			docsById.put(d.docid, d);
			docsInOriginalOrder.add(d);
		}
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

	public void calculateCovariateSummaries() {
		covariateSummaries = new HashMap<>();
		for (String k : schema.varnames()) covariateSummaries.put(k, new SummaryStats());
		for (Document d : allDocs()) {
			for (String varname : schema.varnames()) {
				if (!d.covariates.containsKey(varname)) continue;
				covariateSummaries.get(varname).add((Double) schema.getDouble(d, varname));
			}
		}
		U.p("Covariate summary stats: " + covariateSummaries);
	}
	
	public void convertCovariateTypes() {
		for (Document d : allDocs()) {
			for (String varname : schema.columnTypes.keySet()) {
				if (!d.covariates.containsKey(varname)) continue;
				ColumnInfo ci = schema.columnTypes.get(varname);
				Object converted = schema.columnTypes.get(varname).convertFromJson( (JsonNode) d.covariates.get(varname) );
				d.covariates.put(varname, converted);
				if (ci.dataType==DataType.CATEG && !ci.levels.name2level.containsKey(converted)) {
					ci.levels.addLevel((String) converted);
				}
				
			}
		}
		U.p("Covariate types, after conversion pass: " + schema.columnTypes);
	}

}

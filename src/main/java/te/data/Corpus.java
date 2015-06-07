package te.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import te.data.Schema.ColumnInfo;
import te.data.Schema.DataType;
import utility.util.BasicFileIO;
import utility.util.JsonUtil;
import utility.util.U;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

public class Corpus implements DataLayer {
	public Map<String,Document> docsById;
	public List<Document> docsInOriginalOrder;
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
	
	@Override
	public Collection<Document> allDocs() {
		return docsInOriginalOrder;
	}

	@Override
	public DocSet getDocSet(Collection<String> docids) {
		DocSet ds = new DocSet();
		for (String docid : docids) {
			ds.add( docsById.get(docid) );
		}
		return ds;
	}
	
	public DocSet naiveSelect(String xAttr, String yAttr, double minX, double maxX, double minY, double maxY) {
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
	
	@Override
	public DocSet select(String xAttr, String yAttr, double minX, double maxX, double minY, double maxY) {
		return naiveSelect(xAttr, yAttr, minX, maxX, minY, maxY);
	}
	
	@Override
	public void runTokenizer(Function<String, List<Token>> tokenizer) {
		long t0 = System.currentTimeMillis(); U.p("Running tokenizer");
		for (Document d : docsById.values()) {
			d.tokens = tokenizer.apply(d.text);
		}
		U.pf("Tokenizer completed (%d ms)\n", (System.currentTimeMillis()-t0) );
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
		long t0=System.nanoTime();
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
		U.pf("done finalizing (%.2f ms)\n", 1e-6*(System.nanoTime()-t0));
	}

	/** disjunction query */
	@Override
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
		U.p("Covariate types, before conversion pass: " + schema.columnTypes);
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

	public void setDataFromDataLoader(DataLoader dataloader) {
		U.pf("%d docs loaded total\n", dataloader.docsInOriginalOrder.size());
		this.docsById = dataloader.docsById;
		this.docsInOriginalOrder = dataloader.docsInOriginalOrder;
	}

}

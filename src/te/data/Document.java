package te.data;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;

import com.google.common.collect.DiscreteDomains;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Ranges;

import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;
import te.data.Schema.ColumnInfo;
import te.exceptions.BadData;
import te.ui.GUtil;
import util.BasicFileIO;
import util.JsonUtil;
import util.U;

public class Document {
	public String docid;
	/** starts at 1 */
	public int docnumOriginalOrder;
	public Map<String,Object> covariates;
	public String text;
	public List<Token> tokens;
	public TermVector termVec;
	public Map<Integer, List<TermInstance>> tisByStartTokindex; // not filled in until preanalysis stage
	
	public Document() {
		covariates = new HashMap<>();
	}
	
	static Set<String> SPECIAL_FIELDS;
	static {
		SPECIAL_FIELDS = new HashSet<>();
		SPECIAL_FIELDS.add("docid");
		SPECIAL_FIELDS.add("id");
		SPECIAL_FIELDS.add("text");
	}
	
	/** input: a file with one JSON object per line (each represents one document)
	 * returns the documents, where covariate values are generic JsonNode objects. */
	static List<Document> loadJson(String filename) throws BadData, IOException {
		List<Document> ret = new ArrayList<>();
		int docnum = 1;
		for (String line : BasicFileIO.openFileLines(filename)) {
			String[] parts = line.split("\t");
			String docstr = parts[parts.length-1];
			
			JsonNode j;
			try {
				j = JsonUtil.readJson(docstr);
				Document doc = loadDocFromJson(j, docnum);
				ret.add(doc);
				docnum += 1;
			} catch ( JsonProcessingException e) {
				throw new BadData("invalid JSON: " + docstr);
			}
		}
		return ret;
	}
	
	static Document loadDocFromJson(JsonNode j, int docnum) throws BadData {
		Document doc = new Document();
		JsonNode docidNode = j.has("docid") ? j.get("docid") : j.has("id") ? j.get("id") : null;
		doc.docid = docidNode==null ? "doc"+docnum : docidNode.getTextValue();
		doc.docnumOriginalOrder = docnum;
		if (!j.has("text"))
			throw new BadData("all docs must have a 'text' attribute");
		doc.text = j.get("text").getTextValue();
		for (String key : ImmutableList.copyOf(j.getFieldNames())) {
			if (SPECIAL_FIELDS.contains(key)) continue;
			doc.covariates.put(key, j.get(key));
		}
		return doc;
	}
//	static List<Document> loadXY(String filename) {
//		
//		List<Document> ret = new ArrayList<>();
//		
//		for (String line : BasicFileIO.openFileLines(filename)) {
//			try {
//				Document doc = new Document();
//
//				String[] parts = line.split("\t");
//				doc.covariates.put("x", Double.parseDouble(parts[0]));
//				doc.covariates.put("y", Double.parseDouble(parts[1]));
//				JsonNode j = JsonUtil.readJson(parts[2]);
//				
//				assert j.has("docid") || j.has("id") : "all docs must have a 'docid' or 'id' attribute.";
//				JsonNode docidNode = j.has("docid") ? j.get("docid") : j.has("id") ? j.get("id") : null;
//				doc.docid = docidNode.getTextValue();
//				doc.text = j.get("text").getTextValue();
//
//				ret.add(doc);
//				
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}
//		return ret;
//	}

	public void loadFromNLP(JsonNode jdoc) {
		List<Token> alltoks = new ArrayList<>();
		for (JsonNode jsent : jdoc.get("sentences")) {
			for (int i=0; i<jsent.get("tokens").size(); i++) {
				Token myTok = new Token();
				myTok.text = jsent.get("tokens").get(i).asText();
				myTok.pos = jsent.get("pos").get(i).asText();
				if (jsent.has("ner")) {
					myTok.ner = jsent.get("ner").get(i).asText();
				}
				alltoks.add(myTok);
			}
		}
		tokens = alltoks;
	}
	
	public boolean hasNER() {
		if (tokens.size()==0) return false;
		return tokens.get(0).ner != null;
	}
	
//	public static void main(String[] args) {
//		loadXY(args[0]);
//	}

}

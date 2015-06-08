package te.data;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Document {
	public String docid;
	/** starts at 1 */
	public int docnumOriginalOrder;
	public Map<String,Object> covariates;
	public String text;
	public List<Token> tokens;
	public TermVector termVec;
	public Map<Integer, List<TermInstance>> tisByStartTokindex; // not filled in until preanalysis stage
	public Map<Integer, List<TermInstance>> tisByAllTokindexes; // not filled in until preanalysis stage
	public Map<Integer, List<TermInstance>> tisByStartCharindex; // not filled in until preanalysis stage
	public Map<Integer, List<TermInstance>> tisByEndCharindex; // not filled in until preanalysis stage
	public List<TermInstance> termInstances;
	
	public Document() {
		covariates = new HashMap<>();
	}
	
	public void loadFromNLP(JsonNode jdoc) {
		List<Token> alltoks = new ArrayList<>();
		for (JsonNode jsent : jdoc.get("sentences")) {
			for (int i=0; i<jsent.get("tokens").size(); i++) {
				Token myTok = new Token();
				myTok.text = jsent.get("tokens").get(i).asText();
				if (jsent.has("pos")) {
					myTok.pos = jsent.get("pos").get(i).asText();
				}
				if (jsent.has("ner")) {
					myTok.ner = jsent.get("ner").get(i).asText();
				}
				if (jsent.has("char_offsets")) {
					assert jsent.get("char_offsets").get(i).size()==2 : "char offset should be a 2-length array representing a char span [begin,end)";
					myTok.startChar = jsent.get("char_offsets").get(i).get(0).asInt();
					myTok.endChar = jsent.get("char_offsets").get(i).get(1).asInt();
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

	/** returns -1 if no such token */
	public int getIndexOfFirstTokenAtOrAfterCharIndex(int charind) {
		for (int t=0; t<tokens.size(); t++) {
			if (tokens.get(t).startChar >= charind) {
				return t;
			}
		}
		return -1;
	}
	
	@Override public String toString() { return String.format("Document[docid=%s]", docid); }
	
//	public static void main(String[] args) {
//		loadXY(args[0]);
//	}

}

package d;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;

import com.google.common.collect.DiscreteDomains;
import com.google.common.collect.Lists;
import com.google.common.collect.Ranges;

import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;
import ui.GUtil;
import util.BasicFileIO;
import util.JsonUtil;
import util.U;

public class Document {
	public String docid;
	public double x;
	public double y;
	public String text;
	public List<Token> tokens;
	public TermVector termVec;
	public Map<Integer, List<TermInstance>> tisByStartTokindex; // not filled in until preanalysis stage
	
	static List<Document> loadXY(String filename) {
		
		List<Document> ret = new ArrayList<>();
		
		for (String line : BasicFileIO.openFileLines(filename)) {
			try {
				Document doc = new Document();

				String[] parts = line.split("\t");
				doc.x = Double.parseDouble(parts[0]);
				doc.y = Double.parseDouble(parts[1]);
				JsonNode j = JsonUtil.readJson(parts[2]);
				
				assert j.has("docid") : "all docs must have a 'docid' attribute.";
				doc.docid = j.get("docid").getTextValue();
				doc.text = j.get("text").getTextValue();

				ret.add(doc);
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return ret;
	}

	public void loadFromNLP(JsonNode jdoc) {
		List<Token> alltoks = new ArrayList<>();
		for (JsonNode jsent : jdoc.get("sentences")) {
			for (int i=0; i<jsent.get("tokens").size(); i++) {
				Token myTok = new Token();
				myTok.text = jsent.get("tokens").get(i).asText();
				myTok.pos = jsent.get("pos").get(i).asText();
				myTok.ner = jsent.get("ner").get(i).asText();
				alltoks.add(myTok);
			}
		}
		tokens = alltoks;
	}
	
	public void tokenizationFromText() {
		this.tokens = PreAnalysis.simpleTokenize(this.text);
//		this.tokens = PreAnalysis.stanfordTokenize(this.text);
	}
	
	public static void main(String[] args) {
		loadXY(args[0]);
	}
}

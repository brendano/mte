package d;

import java.io.IOException;
import java.util.*;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;

import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

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

	static StanfordCoreNLP stPipeline;
	static {
	    Properties props = new Properties();
	    props.put("annotators", "tokenize ssplit");
	    stPipeline = new StanfordCoreNLP(props);
	}

	/** run stanford tokenizer, return token list with char offsets */
	static List<Token> tokenize(String text) {
		List<Token> ret = new ArrayList<>();
		
	    Annotation stdoc = new Annotation(text);
	    stPipeline.annotate(stdoc);
        List<CoreMap> sentences = stdoc.get(SentencesAnnotation.class);
        for (CoreMap stSent : sentences) {
            for (CoreLabel stTok: stSent.get(TokensAnnotation.class)) {
            	Token myTok = new Token();
            	myTok.startChar = stTok.beginPosition();
            	myTok.endChar = stTok.endPosition();
            	myTok.text = stTok.value();
            	ret.add(myTok);
            }
        }

        return ret;
	}
	
	static List<Document> load(String filename) {
		List<Document> ret = new ArrayList<>();
		for (String line : BasicFileIO.openFileLines(filename)) {
			try {
				Document doc = new Document();

				String[] parts = line.split("\t");
				doc.x = Double.parseDouble(parts[0]);
				doc.y = Double.parseDouble(parts[1]);
				JsonNode j = JsonUtil.readJson(parts[2]);
				doc.text = j.get("text").getTextValue();
				assert j.has("docid") : "all docs must have a 'docid' attribute.";
				doc.docid = j.get("docid").getTextValue();
				
				doc.tokens = tokenize(doc.text);
				doc.termVec = new TermVector();
				for (Token tok : doc.tokens) {
					doc.termVec.increment(tok.text.toLowerCase());
				}
				ret.add(doc);
				
//				U.p(doc.termVec.map);
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return ret;
	}
	public static void main(String[] args) {
		load(args[0]);
	}
}

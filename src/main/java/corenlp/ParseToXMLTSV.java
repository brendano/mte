package corenlp;
//import com.google.common.collect.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.pipeline.XMLOutputter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;
import nu.xom.Document;
import nu.xom.Serializer;
import util.Arr;
import util.BasicFileIO;
import util.JsonUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * A simpified interface to run the Stanford system, with TSV-streaming-friendly input/output formats.
 * only a few annotation pipeline modes are supported.
 * 
 * Input and output is one line per doc all TSV.
 * 
 * In the input, there are
 *   (1) "passthru" cells, that are not processed here but copied to output
 *   (2) one single "payload" cell, which is JSON
 *   
 * Use case: a docID or other doc-level metadata are passed-thru.
 *   
 * The payload may be either (1) a JSON string, representing a document's text,
 * or (2) the output of "ssplit", a JSON list of strings, one per sentence.
 * The processed version of the payload is the last cell in the output row.
 *
 * passthru and payload cells are specified on the commandline,
 * with 1-based indexing so it looks like unix "cut".
 */
public class ParseToXMLTSV {
	
	static enum Mode {
		SSPLIT, SHALLOW, PARSE, FULL;
	}
	
	public static void main(String[] args) throws IOException {
		String _mode = args[0];
		Mode mode = 
			_mode.equals("ssplit") ? Mode.SSPLIT :
			_mode.equals("shallow") ? Mode.SHALLOW :
			_mode.equals("parse") ? Mode.PARSE :
			_mode.equals("full") ? Mode.FULL : 
			null;
		if (mode==null) throw new RuntimeException("bad mode");
		processDocTSV(Arr.subArray(args, 1, args.length), mode);
	}
	
	public static void processDocTSV(String[] args, Mode mode) throws IOException {
		int[] passthruInds = Arr.readIntVector(args[0], ",");
		int inputInd = Integer.valueOf(args[1]);
		Arr.addInPlace(passthruInds, -1);
		inputInd -= 1;
		
	    // creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, and coreference resolution 
	    Properties props = new Properties();
	    if (mode == Mode.SSPLIT) {
	    	props.put("annotators", "tokenize, ssplit");
	    }
	    else if (mode == Mode.SHALLOW) { 
	    	props.put("annotators", "tokenize, ssplit, pos, lemma, ner");
	    }
	    else if (mode == Mode.PARSE) {
	    	props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse");
	    }
	    else if (mode == Mode.FULL) {
	    	props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
	    }
	    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

	    for (String line : BasicFileIO.STDIN_LINES) {
//			String[] outrow = new String[passthruInds.length + 1];
			String[] passthruCells = new String[passthruInds.length];
			String[] inrow  = line.split("\t");
			for (int out_i=0; out_i < passthruInds.length; out_i++) {
				passthruCells[out_i] = inrow[passthruInds[out_i]];
			}
			
			String textInput = null;
			if (mode == Mode.SSPLIT) {
				textInput = JsonUtil.parse(inrow[inputInd]).asText();
			}
			else if (mode == Mode.FULL || mode == Mode.SHALLOW || mode==Mode.PARSE){
				List<String> sentTokenizedTexts = Lists.newArrayList();
				JsonNode node = JsonUtil.parse(inrow[inputInd]);
				int n = node.size();
				for (int i=0; i<n; i++) {
					String sentTokenization = node.get(i).asText();
					sentTokenizedTexts.add(sentTokenization);
				}
				textInput = StringUtils.join(sentTokenizedTexts, "\n\n");
			}
			
		    // create an empty Annotation just with the given text
		    Annotation document = new Annotation(textInput);
		    pipeline.annotate(document);
		    
	    	System.out.print(StringUtils.join(passthruCells, "\t"));
	    	System.out.print("\t");

	    	if (mode == Mode.SSPLIT) {
	    		String output = createSentenceOutput(document);
	    		System.out.println(output);
	    	}
	    	else if (mode==Mode.FULL || mode==Mode.PARSE || mode==Mode.SHALLOW) {
//	    		pipeline.xmlPrint(document, System.out);
	    		
	    		Document xmldoc = XMLOutputter.annotationToDoc(document, pipeline);
	    		// below is a tweaked version of XMLOutputter.writeXml()
	    		ByteArrayOutputStream sw = new ByteArrayOutputStream();
	    		Serializer ser = new Serializer(sw);
	    		ser.setIndent(0);
	    		ser.setLineSeparator("\n"); // gonna kill this in a moment
	    		ser.write(xmldoc);
	    		ser.flush();
	    		String xmlstr = sw.toString();
	    		xmlstr = xmlstr.replace("\n", "");
	    		System.out.println(xmlstr);
	    	}
		    
		}
	}
	
	static String createSentenceOutput(Annotation document) {
	    // a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
	    ArrayList<CoreMap> sentences = Lists.newArrayList(document.get(SentencesAnnotation.class));
	    List<String> sentenceTokenizations = Lists.newArrayList();
//	    for (CoreMap sentence : sentences) {
	    for (int sentnum=0; sentnum<sentences.size(); sentnum++) {
	    	CoreMap sentence = sentences.get(sentnum);

	    	ArrayList<CoreLabel> stTokens = Lists.newArrayList(sentence.get(TokensAnnotation.class));
    		int T = stTokens.size();
	    	String[] surface = new String[T];
	    	
	    	for (int t=0; t<T; t++) {
	    		String word = stTokens.get(t).get(TextAnnotation.class);
	    		surface[t] = word;
	    	}
	    	
	    	String tokenization = StringUtils.join(surface, " ");
	    	sentenceTokenizations.add(tokenization);
	    }
	    
	    String payload = JsonUtil.toJson(sentenceTokenizations).toString();
	    return payload;
	}
}

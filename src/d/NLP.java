package d;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import ui.GUtil;
import util.Arr;
import util.U;

import com.google.common.collect.Lists;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;

public class NLP {
	static StanfordCoreNLP stPipeline() {
		if (_stPipeline == null) {
		    Properties props = new Properties();
		    props.put("annotators", "tokenize ssplit");
		    _stPipeline = new StanfordCoreNLP(props);
		}
		return _stPipeline;
	};
	private static StanfordCoreNLP _stPipeline;

	public static List<Token> simpleTokenize(String text) {
		String[] toks = text.split("\\s+");
		List<Token> ret = new ArrayList<>();
		for (String tok : toks) {
			Token myTok = new Token();
			myTok.text = tok;
			myTok.startChar=-1;
			myTok.endChar=-1;
			ret.add(myTok);
		}
		return ret;
	}
	
	public static List<Token> stanfordTokenize(String text) {
		List<Token> ret = new ArrayList<>();
		
	    Annotation stdoc = new Annotation(text);
	    stPipeline().annotate(stdoc);
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
	
	// I think this analysis framework is basically the same as Lucene's
	
	public static interface DocAnalyzer { 
		List<TermInstance> analyze(Document doc);
	}
	
	public static class UnigramAnalyzer implements DocAnalyzer {
		public List<TermInstance> analyze(Document doc) {
			List<TermInstance> ret = new ArrayList<>();
			for (int i=0; i<doc.tokens.size(); i++) {
				Token tok = doc.tokens.get(i);
				TermInstance ti = new TermInstance(tok.text.toLowerCase(), Lists.newArrayList(i));
				ret.add(ti);
			}
			return ret;
		}
	}
	
	static boolean isStopword(String w) {
		return w.toLowerCase().matches("^(the|that|a|an|on|of|to|and|but|as|for|-|--|\\.|,|:|;|from|in|with|by)$");
	}
	
	public static class NgramAnalyzer implements DocAnalyzer {
		public int order = 1;
		public boolean posnerFilter = false;
		public boolean stopwordFilter = false;
		
		public List<TermInstance> analyze(Document doc) {
			List<TermInstance> ret = new ArrayList<>();
			for (int i=0; i<doc.tokens.size(); i++) {
				for (int k=1; k<=order; k++) {
					int lastIndex = i+k-1;
					if (lastIndex >= doc.tokens.size()) continue;
					List<Integer> inds = Arr.rangeIntList(i,i+k);

					String s = inds.stream()
							.map(j -> doc.tokens.get(j).text.toLowerCase())
							.collect(Collectors.joining("_"));
					
					if (stopwordFilter) {
//						U.p(k);
//						U.p("---- "+s + " || " + doc.tokens.get(inds.get(0)));
						if (isStopword(doc.tokens.get(i).text) || 
								isStopword(doc.tokens.get(lastIndex).text)) {
//							U.p("STOP " + s);
							continue;
						}
					}
					if (posnerFilter) {
						Token t = doc.tokens.get(inds.get(0));
						assert t.pos != null && t.ner != null : "posFilter=true requires POS&NER preproc.";
//						String poses = inds.stream().map(j->doc.tokens.get(j).pos +":"+j).collect(Collectors.joining("_"));
//						U.p(poses);
//						isLaxerPOSPattern(inds,doc);
						if (isGoodNER(inds,doc) || isBaseNPPOSPattern(inds,doc)) {
							// ok
						}
						else {
//							U.p("REJECT " +s + " ||| " + poses);
							continue;
						}
					}
					ret.add(new TermInstance(s.toLowerCase(), inds));
				}
			}
			return ret;
		}
//		static boolean isLaxerPOSPattern(List<Integer> inds, Document doc) {
//			Set<String> poses = inds.stream().map(i -> doc.tokens.get(i).pos).collect(Collectors.toSet());
//			for (int i : inds.stream().filter(i -> isNominal(doc.tokens.get(i).pos)).collect(Collectors.toList())  ) {
//			}
//			return true;
//		}
		static boolean isBaseNPPOSPattern(List<Integer> inds, Document doc) {
			int lasti = inds.get(inds.size()-1);
			if ( ! isNominal(doc.tokens.get(lasti).pos)) return false;
			boolean jjmode = true;
			for (int i : inds) {
				String pos = doc.tokens.get(i).pos;
				if (jjmode && isAdj(pos)) {
					// ok
				}
				else if (jjmode && isNominal(pos)) {
					// ok and state change
					jjmode = false;
				}
				else if (!jjmode && isNominal(pos)) {
					// ok
				}
				else {
					return false;
				}
			}
			return true;
		}
		static boolean isNominal(String pos) {
			return pos.startsWith("NN") || (pos.equals("N") || pos.equals("^"));
		}
		static boolean isAdj(String pos) {
			return pos.startsWith("JJ") || (pos.equals("A"));
		}
	}
	static boolean isGoodNER(List<Integer> inds, Document doc) {
		if (!doc.hasNER()) return false;
		Set<String> nertags = inds.stream().map(i -> doc.tokens.get(i).ner).collect(Collectors.toSet());
		if (nertags.size()>1) return false;
		String tag = nertags.toArray(new String[0])[0];
		return tag.equals("PERSON") || tag.equals("ORGANIZATION") || tag.equals("LOCATION") || tag.equals("MISC");
	}

	///////////////////////////////////////////////////////////
	
	
	/** edits doc in-place, creating terminstances and termvectors.  assumes tokenization/nlp is complete. */
	public static void analyzeDocument(DocAnalyzer analyzer, Document doc) {
		doc.termVec = new TermVector();
		doc.tisByStartTokindex = new HashMap<>();
		for (TermInstance ti : analyzer.analyze(doc)) {
			doc.termVec.increment(ti.termName);
			int firstIndex = ti.tokIndsInDoc.get(0);
			if (!doc.tisByStartTokindex.containsKey(firstIndex)) {
				doc.tisByStartTokindex.put(firstIndex, new ArrayList<>());
			}
			doc.tisByStartTokindex.get(firstIndex).add(ti);
		}
	}
	
}

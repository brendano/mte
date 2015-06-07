package te.ui;
import te.data.NLP;

public class ExtraInit {
	public static void initWithCode(Main m) throws Exception {
		// the stuff in here is all half-broken
		
//		m.corpus = Corpus.loadXY("/d/sotu/sotu.xy");
		m.corpus = null;
		m.corpus.loadNLP("/d/sotu/sotu.ner");
//		m.corpus.loadSchema("/d/sotu/schema.conf");
		m.xattr = "year";
		m.yattr = "party";
		m.da = new NLP.UnigramAnalyzer();
//		NLP.NgramAnalyzer da = new NLP.NgramAnalyzer() {{ order=1; stopwordFilter=true; posnerFilter=true; }};
//		NLP.NgramAnalyzer da = new NLP.NgramAnalyzer() {{ order=5; stopwordFilter=true; posnerFilter=true; }};
		m.uiOverridesCallback = () -> {
			m.brushPanel.minUserY = -1;
			m.brushPanel.maxUserY = 2;
			return null;
		};
		
//		corpus = Corpus.loadXY("/d/reviews_bryan/ALLnyc.Menu.jsonxy");
//		corpus.runTokenizer(NLP::simpleTokenize);
////		corpus.runTokenizer(NLP::stanfordTokenize);
//		NLP.NgramAnalyzer da = new NLP.NgramAnalyzer() {{ order=2; posnerFilter=false; }};
		
//		corpus = Corpus.loadXY("/d/acl/just_meta.xy");
////		corpus.runTokenizer(NLP::simpleTokenize);
////		corpus.runTokenizer(NLP::stanfordTokenize);
//		corpus.loadNLP("/d/acl/just_meta.ner");
//		corpus.loadLevels("/d/acl/schema.json");
//		NLP.NgramAnalyzer da = new NLP.NgramAnalyzer() {{
//			order=3; posnerFilter=true; stopwordFilter=true;
//		}};
////		afteranalysisCallback = () -> { corpus.indicatorize(); return null; };


//		corpus = Corpus.loadXY("/d/twi/geo2/data/v8/smalltweets2.sample.sort_by_user.useragg.xy.samp2k");
////		corpus = Corpus.loadXY("/d/twi/geo2/data/v8/smalltweets2.sample.sort_by_user.useragg.xy.samp100");
//		corpus.runTokenizer(NLP::simpleTokenize);
//		NLP.DocAnalyzer da = new NLP.UnigramAnalyzer();
////		NLP.NgramAnalyzer da = new NLP.NgramAnalyzer();
////		da.order = 2;
////		da.posnerFilter = false;
//		afteranalysisCallback = () -> { corpus.indicatorize(); return null; };


//		corpus = Corpus.loadXY("/d/bible/by_bookchapter.json.xy.filtered");
//		corpus.loadLevels("/d/bible/schema.json");
//		corpus.runTokenizer(NLP::stanfordTokenize);
//		NLP.DocAnalyzer da = new NLP.UnigramAnalyzer();
////		NLP.NgramAnalyzer da = new NLP.NgramAnalyzer();
////		da.order = 3; da.posnerFilter=false; da.stopwordFilter=true;
//		uiOverridesCallback = () -> { 
//		      brushPanel.minUserY = -1;
//		      brushPanel.maxUserY = 66;
//		      return null;
//		};

		m.finalizeCorpusAnalysisAfterConfiguration();
	}

}

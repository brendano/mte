package d;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.codehaus.jackson.JsonProcessingException;

import com.google.common.collect.Lists;

import ui.Main;
import util.Arr;
import util.U;

public class Analysis {
	static int VIEW_TOTAL = 20;
	
	public static class TermTermAssociations {
		List<String> queryTerms;
		Corpus corpus;
		
		public List<String> topEpmi(int mincount) {
			final List<String> terms = new ArrayList<>();
			final List<Double> leftratios = new ArrayList<>();  // (sum_d n_du n_dv) / n_u
			DocSet docSupp = corpus.select(queryTerms);
			
			U.pf("query term disjunction's numdocs: %s\n", docSupp.docs().size());
			
			// word_assoc.tex notation: 'u' are candidates, 'v' is query
			// MAP:  u => (sum_d n_du n_dv)
			TermVector candInnerProducts = new TermVector();
			
			// build up n_v
			double numtokQueryTermsTotal = 0;
			
			for (Document doc : docSupp.docs()) {
				// n_dv
				double numtokQueryTerms = doc.termVec.valueSum(queryTerms);
				numtokQueryTermsTotal += numtokQueryTerms;
				// n_du
				for (String candTerm : doc.termVec.support()) {
					if (corpus.globalTerms.value(candTerm) < mincount) continue;
					double numtokCandidate = doc.termVec.value(candTerm);
					candInnerProducts.increment(candTerm, numtokQueryTerms * numtokCandidate);
				}
			}
			U.pf("co-occurring num terms: %s\n", candInnerProducts.support().size());
			
			for (Entry<String,Double> e : candInnerProducts.map.entrySet()) {
				String v = e.getKey();
				double inner = e.getValue();
				terms.add(v);
				leftratios.add(inner / corpus.globalTerms.value(v));
			}
			List<Integer> inds = Arr.asList(Arr.rangeInts(terms.size()));
			Collections.sort(inds, Comparator
					.comparing((Integer i) -> -leftratios.get(i))
					.thenComparing((Integer i) -> terms.get(i))
			);
			
			double condoccurZ = condoccurNormalizer(docSupp, queryTerms);
			int j=-1;
			for (int i : inds) {
				if (++j>VIEW_TOTAL) break;
				String w = terms.get(i);
				U.pf("%5d: %25s %6s %6.0f %8.3f %8.3f %8.3f\n", j, w,
						corpus.globalTerms.value(w),
						candInnerProducts.value(w), leftratios.get(i),
						leftratios.get(i)*jointoccurNormalizer(numtokQueryTermsTotal),
						leftratios.get(i)*condoccurZ
						);
			}
			return null;
		}
		
		/** N^2 / (n_v sum_d n_d^2) */
		double jointoccurNormalizer(double n_v) {
			double Nsq = corpus.globalTerms.totalCount * corpus.globalTerms.totalCount;
			return Nsq / (n_v * corpus.doclenSumSq);
		}
		/** N / (sum_d n_d n_dv) */
		double condoccurNormalizer(DocSet queryDocSupp, Collection<String> queryTerms) {
			double sum = 0;
			for (Document doc : queryDocSupp.docs()) {
				double n_dv = doc.termVec.valueSum(queryTerms);
				sum += doc.termVec.totalCount * n_dv;
			}
			return corpus.globalTerms.totalCount / sum;
		}
//		public double epmi(String candidateTerm) {
//		}
	}
	/** can use this for term<->docset association */
	public static class TermvecComparison {
		TermVector focus, background;
		
		public TermvecComparison(TermVector focus, TermVector bg) {
			this.focus=focus;
			this.background = bg;
		}
		
		public double epmi(String term) {
			double myprob = focus.value(term) / focus.totalCount;
			double globalprob = background.value(term) / background.totalCount;
			return myprob / globalprob;
		}
		
		public List<String> topEpmi(double minprob, int mincount) {
			final List<String> terms = new ArrayList<>();
			final List<Double> epmis = new ArrayList<>();
			for (String term : focus.support()) {
				double myprob = focus.value(term) / focus.totalCount;
				double globalprob = background.value(term) / background.totalCount;
				if (myprob < minprob) continue;
				if (focus.value(term) < mincount) continue;

				double ratio = myprob / globalprob;
				terms.add(term);
				epmis.add(ratio);
			}
			List<Integer> inds = Arr.asList( Arr.rangeInts(terms.size()) );
			
			Collections.sort(inds, Comparator
					.comparing((Integer i) -> -epmis.get(i))
					.thenComparing((Integer i) -> -focus.value(terms.get(i)))
					.thenComparing((Integer i) -> terms.get(i))
			);
			List<String> ret = new ArrayList<>();
			for (int i : inds) {
				ret.add(terms.get(i));
			}
			
			U.p("\nepmi");
			int j=-1;
			for (int i : inds) {
				if (++j>VIEW_TOTAL) break;
				String w = terms.get(i);
				U.pf("%5d: %20s %5.3f %4d:%-4d\n", j, terms.get(i), epmis.get(i), (int) focus.value(w), (int) background.value(w) );
				
			}

			return ret;
		}
	}
	
	public static void main(String[] args) throws JsonProcessingException, IOException {
		final Main main = new Main();
		main.initData();
		List<String> termQuery = Lists.newArrayList(args);
		U.p("QUERY:\t" + termQuery);
		
		TermVector focus = main.corpus.select(termQuery).terms;
//		TermVector focus = main.corpus.docsById.get("2000_clinton").termVec;
		TermvecComparison view = new TermvecComparison(focus, main.corpus.globalTerms);
		view.topEpmi(1e-5, 10);
		
		TermTermAssociations tta = new TermTermAssociations();
		tta.corpus = main.corpus;
		tta.queryTerms = termQuery;
		tta.topEpmi(10);
	}
	
}

package d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import util.Arr;
import util.U;

public class Analysis {
	public static class FocusContrastView {
		TermVector focus, background;
		
		public FocusContrastView(TermVector focus, TermVector bg) {
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
			
//			U.p("");
//			int j=-1;
//			for (int i : inds) {
//				j++;
//				U.pf("%5d: %20s %.3f\n", j, terms.get(i), epmis.get(i) );
//				if (j>50) break;
//			}

			return ret;
		}
	}
	
}

package d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Analysis {
	
	/**
	 * exponentiated PMI = p(x|y)/p(x)  (= p(x,y)/[p(x)p(y)])
	 * Provost and Fawcett call this "lift" (ch9, p244)
	 */
	public static List<WeightedTerm> topEPMI(int n, double minthresh, TermVector focus, TermVector background) {
		List<WeightedTerm> arr = new ArrayList<>();
		for (String term : focus.support()) {
			double myprob = focus.value(term) / focus.totalCount;
//			if (focus.value(term) < minthresh) continue;
			if (myprob < minthresh) continue;

			double globalprob = background.value(term) / background.totalCount;
			double ratio = myprob / globalprob;
			arr.add(new WeightedTerm(term, ratio));
		}
		Collections.sort(arr, new WeightedTerm.DescComp());
		return arr.subList(0, Math.min(arr.size(), n));
	}

}

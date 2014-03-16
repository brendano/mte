package d;

import java.util.Comparator;

public class WeightedTerm {
	public String term;
	public double weight;
	
	public WeightedTerm(String _t, double _w) {
		term=_t;
		weight=_w;
	}
	
	static class DescComp implements Comparator<WeightedTerm> {
		@Override
		public int compare(WeightedTerm o1, WeightedTerm o2) {
			return Double.compare(o2.weight, o1.weight);
		}
	}
}

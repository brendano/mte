package te.data;
import java.util.*;
import java.util.Map.Entry;

public class TermVector {
	public Map<String,Double> map;
	public double totalCount = 0;
	
	TermVector() {
		map = new HashMap<>();
	}
	
	public double valueSum(Collection<String> terms) {
		double x = 0;
		for (String t : terms) {
			x += value(t);
		}
		return x;
	}
	
	public void increment(String term, double value) { 
		ensure0(term);
		map.put(term, map.get(term) + value);
		totalCount += value;
	}
	public void increment(String term) {
		increment(term, 1.0);
	}

	public void addInPlace(TermVector other) {
		for (String k : other.map.keySet()) {
			ensure0(k);
			increment(k, other.value(k));
		}
	}
	public double value(String term) {
		if (!map.containsKey(term)) return 0;
		return map.get(term);
	}
	public Set<String> support() {
		// tricky. it would be safer to check for zero-ness. this could be wrong if negative values are ever used in sums.
		return map.keySet();
	}
	
	public TermVector copy() {
		TermVector ret = new TermVector();
		ret.map = new HashMap<>(this.map);
		ret.totalCount = this.totalCount;
		return ret;
	}
	
//	static public TermVector sum(TermVector u, TermVector v) {
//		TermVector ret = u.copy();
//		ret.addInPlace(v);
//		return ret;
//	}
	
	/** helper: ensure that 'term' exists in the map */
	void ensure0(String term) {
		if (!map.containsKey(term)) {
			map.put(term, 0.0);
		}
	}
}

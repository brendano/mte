package util;

/** 
 * Provides online variance and mean, optionally with weighting.
 * via Welford's algorithm (modified to add weights).
 * this could be rewritten without weights to be a little faster.
 * 
 * https://github.com/brendano/running_stat/blob/master/running_stat.cc
 * http://www.johndcook.com/standard_deviation.html
 * 
 * TODO recheck the weighting calculations are correct...
 * i did this a long time ago and didn't write anything down for it.
 */
public class OnlineNormal1dWeighted {

	double ss = 0;           // (running) sum of square deviations from mean
	double m = 0;            // (running) mean
	int n = 0;            // number of items seen
	double totalW =0;        // weight of items seen
	boolean is_started = false;

	public void add(double x) {
		add(x,1);
	}

	public void add(double x, double w) {
		n++;
		if (!is_started) {
			m = x;
			ss = 0;
			totalW = w;
			is_started = true;
		} else {
			double tmpW = totalW + w;
			ss += totalW*w * (x-m)*(x-m) / tmpW;
			m += (x-m)*w / tmpW;
			totalW = tmpW;
		}
	}

	public double var() {
		if (totalW==0) return 0;
		return ss / totalW; 
	}
	public double sd() { 
		return Math.sqrt(var());
	}
	public double mean() { 
		return m;
	}
	public double sumSqDev() {
		return ss;
	}
	public double sum() {
		return m*totalW;
	}
	public int n() {
		return n;
	}
	
	public String toString() {
		return String.format("n=%d totalW=%g mean=%g var=%g sum=%g sumsqdev=%g", n(), totalW, mean(), var(), sum(), sumSqDev());
	}
}

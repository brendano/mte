package util;

/** 
 * Provides online variance and mean via Welford's algorithm.
 * This version supports removing items.
 * 
 * https://github.com/brendano/running_stat/blob/master/running_stat.cc
 * http://www.johndcook.com/standard_deviation.html
 * http://alias-i.com/lingpipe/docs/api/com/aliasi/stats/OnlineNormalEstimator.html
 * 
 */
public class OnlineNormal1d {

	double ss = 0;           // (running) sum of square deviations from mean
	double m = 0;            // (running) mean
	int n = 0;            // number of items seen

	public void add(double x) {
		n++;
		double mNew = m + (x - m)/n;
		ss += (x - m) * (x - mNew);
		m = mNew;
	}
	
	public void remove(double x) {
		assert n > 0;
		if (n == 1) {
			n = 0; m = 0; ss = 0;
			return;
		}
		double mOld = (n * m - x) / (n-1);
		ss -= (x - m) * (x - mOld);
		m = mOld;
		n--;
	}

	public double var() { 
		if (n==0) return 0;
		return ss / n; 
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
		return m*n;
	}
	public int n() {
		return n;
	}
	
	public String toString() {
		return String.format("n=%d mean=%g var=%g sum=%g sumsqdev=%g", n(), mean(), var(), sum(), sumSqDev());
	}
	
	public static void main(String[] args) {
		OnlineNormal1d rv = new OnlineNormal1d();
		for (String line : BasicFileIO.STDIN_LINES){
			double x = Double.valueOf(line);
			U.p("\nADD " + x);
			rv.add(x);
			U.p(rv);
			if (x>0) {
				rv.remove(x);
				U.p("REMOVE " + x);
				U.p(rv);
			}
		}
	}
}

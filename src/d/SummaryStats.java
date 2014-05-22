package d;

import util.OnlineNormal1d;
import util.ReservoirSampler;

public class SummaryStats {
	private OnlineNormal1d meanvar = new OnlineNormal1d();
	private double min=Double.POSITIVE_INFINITY;
	private double max=Double.NEGATIVE_INFINITY;
	
	public void add(double value) {
		meanvar.add(value);
		if (value < min) min = value;
		if (value > max) max = value;
	}
	public double n() { return meanvar.n(); }
	public double mean() { return meanvar.mean(); }
	public double sd() { return meanvar.sd(); }
	public double min() { return min; }
	public double max() { return max; }
	
	public String toString(){ 
		return String.format("SummaryStats(n=%s mean=%s sd=%s min=%s max=%s)",
				n(),mean(),sd(),min(),max());
	}
}

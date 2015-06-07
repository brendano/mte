package util;
import com.google.common.collect.*;

import java.util.*;

/** timer with names and reporting */
public class Timer {
	
	String current = null;
	long currentT0 = -1;
	long objectCreationT0 = -1;
	Map<String, Long> totals;
	
	static Timer _timer;
	static { _timer = new Timer(); }
	
	public static Timer timer() { return _timer; }
	
	public Timer() {
		totals = Maps.newHashMap();
		objectCreationT0 = System.nanoTime();
	}
	public void tick() {
		tick("[default]");
	}
	public void tick(String name) {
		current = name;
		currentT0 = System.nanoTime();
	}
	public long tock() {
		if (current==null) {
			System.out.println("Warning: a tickless tock");
			return -1;
		}
		long elapsed = System.nanoTime() - currentT0;
		long oldsum = totals.containsKey(current) ? totals.get(current) : 0;
		totals.put(current, oldsum + elapsed);
		current = null; currentT0 = -1;
		return elapsed;
	}
	public void tockPrint() {
		long elapsed = tock();
		System.out.println(String.format("%.1f ms", 1e-6 * (double) elapsed));
	}
	@Override
	public String toString() {
		String s = "Times:\n";
		for (Map.Entry<String,Long> e : totals.entrySet()) {
			s += String.format("  %20s:\t", e.getKey());
			double t = (double) e.getValue();
			s += String.format("%8.3f sec, %4.1f hr", t/1e9, 1/1e9/3600);
			s += "\n";
		}
		return s;
	}
	public void report(double totalMS) {
		String s = "Times:\n";
		for (Map.Entry<String,Long> e : totals.entrySet()) {
			s += String.format("  %20s:\t", e.getKey());
			double t = (double) e.getValue();
			
//			s += String.format("%14d ns, %8.3f s", e.getValue(), t/1e9);
			s += String.format("%8.3f sec, %4.1f hr", t/1e9, t/1e9/3600);
			s += String.format(" (%5.2f %%)", 100* (t/1e6) / totalMS);
			s += "\n";
		}
		System.out.println(s);
	}
	public void report() {
		report( (double) (System.nanoTime() - objectCreationT0)*1.0 / 1e6);
	}

}

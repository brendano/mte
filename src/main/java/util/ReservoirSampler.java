package util;

import java.util.ArrayList;
import java.util.List;

/**
 * Vitter reservoir sampler: calculate random sample size 'n' for stream of indeterminate size.
 * At any time, access 'sample' for a random sample of what's been seen so far.
 */
public class ReservoirSampler<T> {
	public List<T> sample;
	int sampleSize;
	int numSeen;
	
	public ReservoirSampler(int sampleSize) {
		sample = new ArrayList<>();
		this.sampleSize = sampleSize;
		numSeen = 0;
	}
	public void add(T x) {
		if (sample.size() < sampleSize) {
			sample.add(x);
		} else if (FastRandom.rand().nextUniform() < sampleSize*1.0 / (numSeen+1)) {
			int replaceIndex = FastRandom.rand().nextInt(sampleSize);
			sample.set(replaceIndex, x);
		}
		numSeen++;
	}
	public static void main(String[] args) {
		ReservoirSampler<String> s = new ReservoirSampler<String>(10);
		
		for (String line : BasicFileIO.STDIN_LINES) {
			s.add(line);
		}
		for (String x : s.sample) {
			U.p(x);
		}
	}
}

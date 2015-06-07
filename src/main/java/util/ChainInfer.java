package util;

import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * Inference algorithms for first-order discrete sequence models.
 *   (1) Max inference (Viterbi)
 *   (2) Marginal inference (Forward-Backward)
 *   (3) TODO: Sampling inference (FB-Sampler)
 *   
 * These functions all take factor exp-potentials as inputs.  (It's your job to compute them.)
 * Intended for: hmm, crf, memm, and other variants.
 * 
 * notation conventions
 * T: length of chain. t=0..(T-1)
 * K: number of latent classes.  k=0..(K-1)
 * All factors are in exp space -- because we use per-timestep renormalization, which is much faster than log-space arithmetic.  See Rabiner 1989.
 * (Thus all factor inputs are non-negative.  we could use negative values for NA's or hard-constraint-zeros or something.)
 * 
 * We have no notion of special start or stop states.  you have to handle that yourself: build their effects
 * into the first or last factor tables yourself.
 * We have no notion of learning.  This might be a subroutine for that.
 * 
 * TESTS: uncomment the appropriate main() next to the relevant textX(), then from the "chains" subdir run:
 *     Rscript sim_chains.r && ../java.sh util.ChainInfer obsF transF
 * and look to make sure the dynamic programming algo agrees with the exhaustive solution, or that it doesn't crash, etc.
 * 
 * @author brendano
 */
public class ChainInfer {
	
	/**
	 * Viterbi algorithm
	 * @param obsFactors:  size T x K;  (t, class)
	 * @param transFactors:  size K x K;  (class@t, class@{t+1})
	 *            ... note the algorithm can be extended to size (T-1) x K x K, there is commented out code for this
	 * @return discrete sequence length T, each value in {0..(K-1)}
	 */
	public static int[] viterbi(double[][] obsFactors, double[][] transFactors) {
		if (obsFactors.length==0) return new int[0];
		final int T = obsFactors.length;
		final int K = obsFactors[0].length;
		
		// viterbi and backpointer tables
		double[][] V = new double[T][K];
		int[][] backs = new int[T][K];  // backs[0] will go unused
		for (int k=0; k<K; k++)
			V[0][k] = obsFactors[0][k];
		
		double[] scores = new double[K];
		
		for (int t=1; t<T; t++) {
			for (int k=0; k<K; k++) {
				for (int prev=0; prev<K; prev++) {
//					scores[prev] = V[t-1][prev] * transFactors[t-1][prev][k] * obsFactors[t][k];
					scores[prev] = V[t-1][prev] * transFactors[prev][k] * obsFactors[t][k];
				}
				int best_prev = Arr.argmax(scores);
				double best_score = scores[best_prev];
				V[t][k] = best_score;
				backs[t][k] = best_prev;
			}
		}
		
		int[] path = new int[T];
		path[T-1] = Arr.argmax(V[T-1]);
		for (int t=T-2; t>=0; t--) {
			path[t] = backs[t+1][path[t+1]];
		}
		
		return path;
	}
	
	static int[] exhaustiveSearch(final double[][] obsFactors, final double[][] transFactors) {
		final int T = obsFactors.length, K = obsFactors[0].length;
		// stupid singleton array hack so the callback can modify the values
		final int[][] best = new int[][]{ null };
		final double[] bestScore = new double[]{ Double.NEGATIVE_INFINITY };
		exhaustiveCalls(T,K, new Function<int[],Object>() {
			@Override
			public Object apply(int[] ys) {
				double s = computeSolutionScore(ys, obsFactors, transFactors);
				if (s > bestScore[0]) {
					best[0] = Arr.copy(ys);
					bestScore[0] = s;
				}
				return null;
			}
		}
		);
		return best[0];
	}
	
	/** to test forward-backward */
	static double[][] exhaustiveMarginals(final double[][] obsFactors, final double[][] transFactors) {
		final int T = obsFactors.length, K = obsFactors[0].length;
		final double[][][] expectedCounts = new double[1][T][K];
		exhaustiveCalls(T,K, new Function<int[],Object>() {
			@Override
			public Object apply(final int[] ys) {
				double s = computeSolutionScore(ys, obsFactors, transFactors);
				double weight = Math.exp(s);  assert weight > 0 : "underflow";
//				U.p("sol " + Arrays.toString(ys) + " weight " + weight);
				for (int t=0; t<T; t++) {
					expectedCounts[0][t][ys[t]] += weight;
				}
				return null;
			}
		});
		for (int t=0; t<T; t++)
			Arr.normalize(expectedCounts[0][t]);
		return expectedCounts[0];
	}

	static double[][][] exhaustivePairMarginals(final double[][] obsFactors, final double[][] transFactors) {
		final int T = obsFactors.length, K = obsFactors[0].length;
		final double[][][][] expectedCounts = new double[1][T-1][K][K];
		exhaustiveCalls(T,K, new Function<int[],Object>() {
			@Override
			public Object apply(final int[] ys) {
				double s = computeSolutionScore(ys, obsFactors, transFactors);
				double weight = Math.exp(s);  assert weight > 0 : "underflow";
//				U.p("sol " + Arrays.toString(ys) + " weight " + weight);
				for (int t=0; t<T-1; t++) {
					expectedCounts[0][t][ys[t]][ys[t+1]] += weight;
				}
				return null;
			}
		});
		for (int t=0; t<T-1; t++)
			Arr.normalize(expectedCounts[0][t]);
		return expectedCounts[0];
	}
	/** score is unnorm logprob. but assume EXP scaled factor potentials,
	 * since that's more convenient for the other algos in this file. */
	static double computeSolutionScore(int[] ys, double[][] obsFactors, double[][] transFactors) {
		int T = ys.length;
		double score = 0;
		for (int t=0; t<T; t++) {
			score += Math.log(obsFactors[t][ys[t]]);
			if (t<T-1)
				score += Math.log(transFactors[ys[t]][ys[t+1]]);
		}
		return score;
	}
	
	/**
	 * Calls the callback on every possible sequence, so K^T calls total.
	 * 
	 * WARNING, this RESUSES the same callback array.  if you want to store it, need to copy it!!
	 *  would be nicer to invert this into a guava-friendly iterator form. how?
	 * need an explicit agenda structure, i think.
	 * function recursion is just so much easier to write. python generators make it easy to switch between the modes but alas.
	 */
	public static void exhaustiveCalls(int T, int K, Function<int[], Object> callback) {
		int[] values = Arr.repInts(-1, T);
		exhaustiveCallsRecurs(0, T, K, values, callback);
	}

	/** note this overwrites values[] */
	static void exhaustiveCallsRecurs(final int t, final int T, final int K, int[] values, Function<int[], Object> callback) {
		if (t==T) {
			callback.apply(values);
		} else {
			for (int k=0; k<K; k++) {
				values[t] = k;
				exhaustiveCallsRecurs(t+1, T, K, values, callback);
			}
		}
	}
	
	/** testing: ensure this gives the correct number of outputs: ./java.sh util.ChainInfer | wc -l */
	static void testExhaustiveCalls() {
		exhaustiveCalls(3, 3, new Function<int[],Object>() {
			@Override
			public Object apply(int[] values) {
				U.p(values);
				return null;
			}
		}
		);
	}
//	public static void main(String[] args) { testExhaustiveCalls(); }
	
	/** My test procedure.  make sure the algos agree.
	 * 
~/myutil/chains % Rscript sim_chains.r && ../java.sh util.ChainInfer obsF transF
EXHAUS	[0, 1, 1, 1, 0]
VITERBI	[0, 1, 1, 1, 0]
	 */
	static void testMaxInference(String[] args) {
		double[][] obsF = Arr.readDoubleMatrix(args[0]);
		double[][] transF= Arr.readDoubleMatrix(args[1]);
		U.pf("EXHAUS\t");  U.p(exhaustiveSearch(obsF,transF));
		U.pf("VITERBI\t");  U.p(viterbi(obsF,transF));
	}
//	public static void main(String[] args) { testMaxInference(args); }
	
	static void testProbInference(String[] args) {
		double[][] obsF = Arr.readDoubleMatrix(args[0]);
		double[][] transF= Arr.readDoubleMatrix(args[1]);
		U.pf("VITERBI\t");  U.p(viterbi(obsF, transF));
		double[][] marginals1 = forwardBackward_justLabels(obsF, transF);
		U.p("FB_MAR");
		U.p(marginals1);
		Marginals m = forwardBackward(obsF, transF);
		U.p("FB_MAR2");
		U.p(m.labelMarginals);
		U.p("EX_MAR");
		double[][] marginals_e = exhaustiveMarginals(obsF, transF);
		U.p(marginals_e);
	}
//	public static void main(String[] args) { testProbInference(args); }
	
	static void printPairMar(double[][][] m) {
		for (int t=0;t<m.length;t++) {
			U.pf("(%d--%d)\n", t,t+1);
			U.p(m[t]);
		}
	}
	
	static void testPairMarginals(String[] args) {
		double[][] obsF = Arr.readDoubleMatrix(args[0]);
		double[][] transF= Arr.readDoubleMatrix(args[1]);
		Marginals m1 = forwardBackward(obsF,transF);
		U.p("PAIRMAR");
		printPairMar(m1.pairMarginals);
		U.p("EX");
		printPairMar(exhaustivePairMarginals(obsF,transF));
	}
//	public static void main(String[] args) { testPairMarginals(args); }

	/**
	 * P(y_t=k | x_1:T) 
	 */
	static ForwardOrBackwardTables forward(double[][] obsFactors, double[][] transFactors) {
		final int T = obsFactors.length;
		final int K = obsFactors[0].length;
		ForwardOrBackwardTables f = new ForwardOrBackwardTables();
		f.probs = new double[T][K];
		f.incrementalNormalizers = new double[T];
		double Z;

		f.probs[0] = Arr.copy(obsFactors[0]);
		Z = Arr.sum(f.probs[0]);
		Arr.multiplyInPlace(f.probs[0], 1.0/Z);
		f.incrementalNormalizers[0] = Z;
		
		for (int t=1; t<T; t++) {
			for (int k=0; k<K; k++) {
				for (int prev=0; prev<K; prev++) {
//					f.probs[t][k] += f.probs[t-1][prev] * transFactors[t-1][prev][k];
					f.probs[t][k] += f.probs[t-1][prev] * transFactors[prev][k];
				}
				f.probs[t][k] *= obsFactors[t][k];
			}
			Z = Arr.sum(f.probs[t]);
			Arr.multiplyInPlace(f.probs[t], 1/Z);
			f.incrementalNormalizers[t] = Z;
		}
		return f;
	}

	/** convention for this data structure:
	 * 'probs' is normalized for each timestep!
	 * and 'incrementalNormalizers' lets you reconstruct the true forward probs, if desired.
	 */
	static class ForwardOrBackwardTables {
		/** size T x K */
		public double[][] probs;
		/** size T */
		public double[] incrementalNormalizers;
	}
	
	
	/** unnormalized (underflow-prone) P(y_t=k | x_(t+1):T) */ 
	static ForwardOrBackwardTables backwardNoNormalization(double[][] obsFactors, double[][] transFactors) {
		final int T = obsFactors.length;
		final int K = obsFactors[0].length;
		double[][] backwardProbs = new double[T][K];
		backwardProbs[T-1] = Arr.rep(1.0, K);

		for (int t=T-2; t>=0; t--) {
			for (int next=0; next<K; next++) {
				for (int k=0; k<K; k++) {
					backwardProbs[t][k] += obsFactors[t+1][next] * transFactors[k][next]  * backwardProbs[t+1][next];
				}
			}
		}
		ForwardOrBackwardTables b = new ForwardOrBackwardTables();
		b.probs = backwardProbs;
		return b;
//		return backwardProbs;
	}

	/** P(y_t=k | x_(t+1):T) */ 
	static ForwardOrBackwardTables backward(double[][] obsFactors, double[][] transFactors) {
		final int T = obsFactors.length;
		final int K = obsFactors[0].length;
		ForwardOrBackwardTables b = new ForwardOrBackwardTables();
		b.probs = new double[T][K];
		b.incrementalNormalizers = new double[T];
		b.probs[T-1] = Arr.rep(1.0/K, K);
		b.incrementalNormalizers[T-1] = 1;

		for (int t=T-2; t>=0; t--) {
			for (int k=0; k<K; k++) {
				for (int next=0; next<K; next++) {
					b.probs[t][k] += obsFactors[t+1][next] * transFactors[k][next]  * b.probs[t+1][next];
				}
			}
			double Z = Arr.sum(b.probs[t]);
			Arr.multiplyInPlace(b.probs[t], 1.0/Z);
			b.incrementalNormalizers[t] = Z;
		}
		return b;
	}
	
	/** takes output of forward algo as input */
	static int[] backwardSample(ForwardOrBackwardTables forwards, double[][] obsFactors, double[][] transFactors) {
		int T = obsFactors.length;
		int K = obsFactors[0].length;
		int[] sample = new int[T];
		double[] field = new double[K];
		sample[T-1] = FastRandom.rand().nextDiscrete(forwards.probs[T-1]);
		for (int t=T-2; t>=0; t--) {
			Arr.fill(field,0);
			double Z = 0;
			for (int k=0; k<K; k++) {
				int next = sample[t+1];
				field[k] += obsFactors[t+1][next] * transFactors[k][next]  * forwards.probs[t][k];
				Z += field[k];
			}
			sample[t] = FastRandom.rand().nextDiscrete(field, Z);
		}
		return sample;
	}
	
	public static List<int[]> forwardBackwardSample(int nSamples, double[][] obs, double[][] trans) {
		List<int[]> samples = Lists.newArrayList();
		ForwardOrBackwardTables f = forward(obs,trans);
		for (int s=0; s<nSamples; s++) {
			int[] sample = backwardSample(f, obs, trans);
			samples.add(sample);
		}
		return samples;
	}
	
	static void testForwardBackwardSample(String args[]) {
		double[][] obs = Arr.readDoubleMatrix(args[0]);
		double[][] trans= Arr.readDoubleMatrix(args[1]);
		int T=obs.length; int K=obs[0].length;
		double[][] mar = forwardBackward(obs,trans).labelMarginals;
		U.p("FB_MAR");
		U.p(mar);
		double[][] emar = new double[T][K];
		int i=0;
		int S = 10000;
		for (int[] s : forwardBackwardSample(S, obs,trans)) {
//			U.pf("S_%s\t", (++i));
//			U.p(s);
			for (int t=0; t<s.length; t++) {
				emar[t][s[t]] += 1;
			}
		}
		Arr.multiplyInPlace(emar, 1.0/S);
		U.p("E_MAR");
		U.p(emar);
		
		double[] diffs = Arr.pairwiseSubtract(Arr.flatten(mar), Arr.flatten(emar));
		double meandiff = Arr.mean(Arr.abs(diffs));
		U.p("meandiff " + meandiff);
	}
	public static void main(String args[]) { testForwardBackwardSample(args); }

	
	static final double DANGEROUSLY_LOW = 1e-100;
	
	/** Compute the final label marginals P(y_t=k | x_1:T), given the forward probs as input.
	 * (Thus this function runs the backward algorithm internally)
	 * This function hasn't been significantly tested. haven't thought through the implications of the conservative normalization strategy here.
	 */
	static double[][] marginalsFromForwardViaBackward(ForwardOrBackwardTables forwards, double[][] obsFactors, double[][] transFactors) {
		final int T = obsFactors.length;
		if (T==0) return new double[0][0];
		final int K = obsFactors[0].length;
		double[][] backwardProbs = new double[T][K];
		backwardProbs[T-1] = Arr.rep(1.0, K);
		double[][] marginals = new double[T][K];
		marginals[T-1] = Arr.copy(forwards.probs[T-1]);

		for (int t=T-2; t>=0; t--) {
			boolean needsRenorm = false;
			for (int k=0; k<K; k++) {
				for (int next=0; next<K; next++) {
					backwardProbs[t][k] += obsFactors[t+1][next] * transFactors[k][next]  * backwardProbs[t+1][next];
				}
				if (backwardProbs[t][k] < DANGEROUSLY_LOW) {
					needsRenorm = true;
				}
			}
			if (needsRenorm) {
				Arr.normalize(backwardProbs[t]);
			}
			marginals[t] = Arr.pairwiseMultiply(forwards.probs[t], backwardProbs[t]);
			Arr.normalize(marginals[t]); // could speedup by combining multiply and normalization into one pass
		}
		return marginals;
	}
	
	public static class Marginals {
		/** size T x K: p(y_t) */
		public double[][] labelMarginals;
		/** size (T-1) x K x K:  p(y_t, y_t+1) */
		public double[][][] pairMarginals;
	}
	
	/**
	 * @return matrix of marginals size (T x K): for each (t,k):  p(y_t = k | x_1 .. x_T).
	 */
	public static double[][] forwardBackward_justLabels(double[][] obsFactors, double[][] transFactors) {
		int T = obsFactors.length;
		int K = obsFactors[0].length;
		ForwardOrBackwardTables f = forward(obsFactors, transFactors);
		ForwardOrBackwardTables b = backward(obsFactors, transFactors);
		double[][] marginals = new double[T][K];
		for (int t=0; t<T; t++) {
			marginals[t] = Arr.pairwiseMultiply(f.probs[t], b.probs[t]);
			Arr.normalize(marginals[t]);
		}
		return marginals;
	}
	
	/** Compute both single-label and pair marginals via Forward-Backward */
	public static Marginals forwardBackward(double[][] obsFactors, double[][] transFactors) {
		int T = obsFactors.length;
		int K = obsFactors[0].length;
		ForwardOrBackwardTables f = forward(obsFactors, transFactors);
		ForwardOrBackwardTables b = backward(obsFactors, transFactors);
		
		Marginals m = new Marginals();
		m.labelMarginals = new double[T][K];
		m.pairMarginals = new double[T-1][K][K];

		for (int t=0; t<T; t++) {
			m.labelMarginals[t] = Arr.pairwiseMultiply(f.probs[t], b.probs[t]);
			Arr.normalize(m.labelMarginals[t]);
			if (t < T-1) {
				// p(y_t =k and y_t-1 =j)
				for (int k=0; k<K; k++)
					for (int j=0; j<K; j++)
						m.pairMarginals[t][k][j] = f.probs[t][k] * b.probs[t+1][j] * transFactors[k][j] * obsFactors[t+1][j];
				double Z = Arr.sum(m.pairMarginals[t]);
				Arr.multiplyInPlace(m.pairMarginals[t], 1.0/Z);
			}
		}
		return m;
	}

	/** just run it.  use T=big for a good test.
	 * for T=10000 I get an error under backwardNoNormalization()
	 * but no error under backward() (that has the renormalization) */
	static void testUnderflows(String[] args) {
		double[][] obsF = Arr.readDoubleMatrix(args[0]);
		double[][] transF= Arr.readDoubleMatrix(args[1]);
		Marginals m = forwardBackward(obsF,transF);
		assert Arr.isFinite(m.labelMarginals);
		assert Arr.isFinite(m.pairMarginals);
	}
//	public static void main(String[] args) { testUnderflows(args); }
	
}

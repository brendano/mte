package util;
import com.google.common.base.Function;
import com.google.common.collect.*;

import java.util.*;

public class MCMC {

	/**
	    Slice sampling (Neal 2003; MacKay 2003, sec. 29.7)

	    logdist: log-density function of target distribution
	    initial: initial state  (D-dim vector)
	    widths: step sizes for expanding the slice (D-dim vector)

	    This is my port of Iain Murray's
	    http://homepages.inf.ed.ac.uk/imurray2/teaching/09mlss/slice_sample.m 
	    which in turn derives from MacKay.  Murray notes where he found bugs in
	    MacKay's pseudocode... good sign
	 **/
	public static List<double[]> slice_sample(
			Function<double[], Double> logdist, double[] initial, double[] widths, int niter) {
		boolean step_out = true;
		final int D = initial.length;
		assert widths.length == D;

		double[] state = initial;
		double log_Px = logdist.apply(state);

		List<double[]> history = Lists.newArrayList();

		for (int itr=0; itr < niter; itr++) {
			//	        U.pf("Slice iter %d stats %s log_Px %f\n",itr, Arr.sf("%.3f", state), log_Px);
			//	        if (itr%100==0) { U.pf("."); System.out.flush(); }
			double log_uprime = Math.log(Math.random()) + log_Px;

			//	        # Sweep through axes
			for (int dd=0; dd < D; dd++) {
				double[] 
				       x_l  = Arrays.copyOf(state, D),
				       x_r	= Arrays.copyOf(state, D),
				       xprime = Arrays.copyOf(state, D);
				//	            # Create a horizontal interval (x_l, x_r) enclosing xx
				double r = Math.random();
				x_l[dd] = state[dd] - r*widths[dd];
				x_r[dd] = state[dd] + (1-r)*widths[dd];
				if (step_out) {
					while (logdist.apply(x_l) > log_uprime)
						x_l[dd] -= widths[dd];
					while (logdist.apply(x_r) > log_uprime)
						x_r[dd] += widths[dd];
				}
				//	            # Inner loop:
				//	            # Propose xprimes and shrink interval until good one is found.
				double zz = 0;
				while (true) {
					zz += 1;
					xprime[dd] = Math.random() * (x_r[dd] - x_l[dd]) + x_l[dd];
					log_Px = logdist.apply(xprime);
					if (log_Px > log_uprime) {
						break;
					} else {
						if (xprime[dd] > state[dd]) {
							x_r[dd] = xprime[dd];
						} else if (xprime[dd] < state[dd]) {
							x_l[dd] = xprime[dd];
						} else {
							assert false : "BUG, shrunk to current position and still not acceptable";
						}
					}
				}
				state[dd] = xprime[dd];
			}
			history.add(Arrays.copyOf(state, D));
		}
		return history;
	}

	public static interface ProposalDensity {
		public double apply(double[] currentState, double[] proposedState);
	}
	
	/**
	 * the original Metropolis algorithm: assume a symmetric proposal distribution, 
	 * so the hastings correction is unnecessary. 
	 * thus we only need a proposer, don't need the conditional proposal density.
	 * For full Metropolis-Hastings, see {@link #hastings}.
	 */
	public static MHResult metropolis(
			Function<double[],Double> 	targetLogDensity,
			Function<double[],double[]> proposer,
			double[] initial, int numIter, FastRandom rand) {
		int numAccepts = 0;

		List<double[]> history = Lists.newArrayList();
		double[] currentState = initial;
		double currentLogProb = targetLogDensity.apply(currentState);

		for (int iter=0; iter < numIter; iter++) {
			double[] xprime = proposer.apply(currentState);
			double xprimeLogProb = targetLogDensity.apply(xprime);
			if (xprimeLogProb > currentLogProb) {
				// accept!
				numAccepts++;
				currentState = xprime;
				currentLogProb = xprimeLogProb;
			} else {
				double alpha = Math.exp(xprimeLogProb - currentLogProb);
				if (rand.nextUniform() < alpha) {
					// accept!
					numAccepts++;
					currentState = xprime;
					currentLogProb = xprimeLogProb;
				}
			}
			history.add(currentState);
		}
		MHResult r = new MHResult();
		r.history = history;
		r.acceptRate = 1.0*numAccepts / numIter;
		return r;
	}
	
	public static class MHResult {
		public double acceptRate = -1;
		public List<double[]> history;
		
		public double[] last() {
			return history.get(history.size()-1);
		}
	}
	/**
	 * the full Metropolis-Hastings algorithm.
	 * Note all densities are in logprobs.
	 * (for a symmetric proposal, use {@link #metropolis} which will be faster)
	 */
	public static MHResult hastings(
			Function<double[],Double> 	targetLogDensity,
			ProposalDensity 			proposalLogDensity,
			Function<double[],double[]> proposer,
			double[] initial, int numIter, FastRandom rand)
	{
		int numAccepts = 0;
		List<double[]> history = Lists.newArrayList();
		double[] x = initial;  					  // current state
		double lp_x = targetLogDensity.apply(x);  // current state's logprob
		
		for (int iter=0; iter < numIter; iter++) {
			double[] xnew = proposer.apply(x);

//			double q_x_given_xprime = Math.exp(proposalLogDensity.apply(xprime, currentState));
//			double q_xprime_given_x = Math.exp(proposalLogDensity.apply(currentState, xprime));
//			double xprimeProb = Math.exp(targetLogDensity.apply(xprime));
//			double alpha = xprimeProb * q_x_given_xprime / (currentProb * q_xprime_given_x);
			
			double lq_old_from_new = proposalLogDensity.apply(xnew, x);
			double lq_new_from_old = proposalLogDensity.apply(x, xnew);
			double lp_new 	 = targetLogDensity.apply(xnew);
			double lalpha = lp_new - lp_x + lq_old_from_new - lq_new_from_old;
			
			if (lalpha >= 0) {
				// accept!
				numAccepts++;
				x = xnew;
				lp_x = lp_new;
			} else {
				double u = rand.nextUniform();
				if (u < Math.exp(lalpha)) {
					// accept!
					numAccepts++;
					x = xnew;
					lp_x = lp_new;
				}
			}
			history.add(x);
		}
		MHResult r = new MHResult();
		r.history = history;
		r.acceptRate = 1.0*numAccepts / numIter;
		return r;
	}


	static double triangleLP(double x) {
		boolean in_tri = 0<x && x<20;
		if (!in_tri) return -1e100;
		double p = (x* (x<10 ? 1 : 0) + (20-x)*(x>=10 ? 1 : 0)) / 5;
		return Math.log(p);
	}

	/* Visual testing.  Would be nice to Cook-Gelman-Rubin-style QQplot against truth.
		> x=read.table(pipe("grep SLICE out"))$V2
		> plot(x)
		> acf(x)
		> plot(table(round(x)))
	 */
	static void triangleTest_Slice() {
		Function<double[],Double> logdist = new Function<double[],Double>() {
			@Override
			public Double apply(double[] input) {
				return triangleLP(input[0]);
			}
		};
		List<double[]> history = MCMC.slice_sample(logdist, new double[]{5}, new double[]{1}, 10000);
		for (double[] h : history) {
			U.p(h[0]);
		}
	}
	
	static void triangleTest_MH() {
		Function<double[],Double> logdist = new Function<double[],Double>() {
			@Override
			public Double apply(double[] input) {
				return triangleLP(input[0]);
			}
		};
		ProposalDensity logq = new ProposalDensity() {
			@Override
			public double apply(double[] currentState, double[] proposedState) {
				return Math.log(0.5);
			}
		};
		Function<double[],double[]> proposer = new Function<double[],double[]>() {
			@Override
			public double[] apply(double[] current) {
				double cur = current[0];
				if (cur==0) return new double[]{1};
				if (cur==19) return new double[]{18};
				if (FastRandom.rand().nextUniform() <= 0.5) {
					return new double[]{cur-1};
				} else {
					return new double[]{cur+1};
				}
			}
		};

		MHResult r = MCMC.hastings(logdist, logq, proposer, new double[1], 10000, FastRandom.rand());
//		MHResult r = MCMC.metropolis(logdist, proposer, new double[1], 10000, FastRandom.rand());
		for (double[] h : r.history) {
			U.p(h[0]);
		}
	}

	public static void main(String[] args) {
//		triangleTest_Slice();
		triangleTest_MH();
	}

}

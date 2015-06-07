package util;

import com.google.common.base.Function;

import java.util.*;


/**
 * Routines for posterior inference of the logistic normal "eta" parameter in
 *      (mu,s2) -> eta -> counts
 * 		eta ~ N(mu,s2), counts ~ Mult(n, softmax(eta))
 * Goal is to infer
 * 		p(eta | mu, s2, counts)
 * 
 * using K dimensions for eta, and K+1 dimensions for counts.
 * 
 */
public class LNInference {

	/**
	 * Use slice sampling to draw a single sample ("eta") from posterior 
	 * P(eta | counts, mu,s) \propto P(counts|eta) P(eta|mu,s)
	 * count observations and logistic normal prior (diagonal covar)
	 * 
	 * Very slow but seems to yield correct results.
	 * 
	 * suggestion: numSliceIter=30
	 */
	public static double[] sampleSlice(int numSliceIter,
			final double[] counts, final double[] etaMean, final double etaVar[])
	{
		List<double[]> history = sampleSliceHistory(numSliceIter, counts, etaMean, etaVar);
		return history.get(history.size()-1);
	}
	
	/** like {@link #sampleSlice} but give sampler history */
	public static List<double[]> sampleSliceHistory(int numSliceIter,
			final double[] counts, final double[] etaMean, final double etaVar[])
	{
		final int K = etaMean.length;
		Function<double[], Double> lDensity  = new Function<double[],Double>() {
			@Override
			public Double apply(double[] eta) {
				return calcUnnormLogprob(eta, counts, etaMean, etaVar);
			}
		};
		double[] widths = new double[K];
		Arrays.fill(widths, 10);
		double[] init = new double[K];
		return MCMC.slice_sample(lDensity, init, widths, numSliceIter);
	}

	/** 
	 * unnorm logprob f(eta) =
	 * = log P(counts | eta) P(eta | mu,s)
	 */
	public static double calcUnnormLogprob(final double[] eta,
			final double[] counts, final double[] etaMean, final double etaVar[])
	{
		final int K = eta.length + 1;
		double[] theta = Arr.softmax1(eta);
		double ll = 0;
		for (int k=0; k < K; k++) {
			ll += counts[k] * Math.log(theta[k]);
			if (k < K-1)
				ll += -1/(2*etaVar[k]) * Math.pow(eta[k] - etaMean[k], 2);
		}
		return ll;
	}

	/** hessian for positive logprob */
	public static double[][] calcEtaHessian(double[] eta,
	        double counts[], double totalCount, double etaMean[], double etaVar[])
	{
		final int K = eta.length;
		double[][] hessian = new double[K][K];
		double theta[] = Arr.softmax1(eta);
		for (int i=0; i<K; i++) {
			hessian[i][i] = -totalCount*theta[i]*(1-theta[i]) - 1.0/etaVar[i];
			for (int j=i+1; j<K; j++) {
				hessian[i][j] = totalCount*theta[i]*theta[j];
				hessian[j][i] = hessian[i][j];
			}
		}
		return hessian;
	}
	
	/** diagonal elements of hessian for positive logprob */ 
	public static double[] calcEtaHessianDiagonal(double[] eta,
			double counts[], double totalCount, double etaMean[], double etaVar[])
	{
		int K = eta.length;
		double[] hessDiag = new double[K];
		double theta[] = Arr.softmax1(eta);
		for (int i=0; i<K; i++) {
			hessDiag[i] = -totalCount*theta[i]*(1-theta[i]) - 1.0/etaVar[i];
		}
		return hessDiag;
	}
	
	/** covar = [-H(logpost)]^-1
	 * and here we use the diagonalized hessian, thus diag covar is simple.
	 * (technical note, this is NOT the diag covar of the full MVN approx.)
	 */
	static double[] calcEtaLaplaceDiagVar(double[] etaMode,
			double counts[], double totalCount, double etaMean[], double etaVar[]) {
		double[] x = calcEtaHessianDiagonal(etaMode, counts, totalCount, etaMean, etaVar);
		Arr.multiplyInPlace(x, -1);
		Arr.powInPlace(x, -1);
		return x;
	}

	/**
	 * Logistic normal logposterior gradient at one eta.
	 */
	public static double[] calcEtaGradient(
			final double[] eta, final double[] theta,
			final double counts[], final double totalCount, final double etaMean[], final double etaVar[])
	{
		final int K = eta.length;
		double[] gradient = new double[K];
		for (int k=0; k < K; k++) {
			double diff      = counts[k] - totalCount*theta[k];
			double priorGrad = -1/etaVar[k] * (eta[k] - etaMean[k]);
			gradient[k] = diff + priorGrad;
		}
		return gradient;
	}

	/** quadratic time (instead of cubic)
	 * TODO figure out how to K-1-ify this
	 */
	static double[][] calcEtaInvHessianViaSM(double[] theta, double totalCount, double etaVar) {
		// um why doesn't this involve etaMean?  the magic of second derivatives i guess
		final int K = theta.length;
		double[] diag = new double[K];
		for (int k=0; k<K; k++) {
			diag[k] = 1.0/(totalCount*theta[k] + 1/etaVar);
		}
		// NOTE could save a few multiplies in here by unpacking the SM formula or parameterizing the call with appropriate scalars
		double[] uvec = Arr.multiply(theta, Math.sqrt(totalCount));
		double[][] hessInv = Util.diagSM(diag, uvec, Arr.multiply(uvec, -1));
		return hessInv;
	}
	
    final static double newtonRelativeTol = 1e-2;
    final static double newtonLinesearchMaxIter = 5;

    /** 
     * MAP estimate eta, under diag prior covar, using linear-time SM-Newton method.
     * suggestion: maxIter=20 is plenty.  even maxIter=5 gives pretty good answers.
     */
	public static double[] estNewton(int maxIter, double[] init,
			final double[] counts, final double totalCount,
			final double[] etaMean, final double etaVar[]) 
    {
    	final int K = etaMean.length;
    	double[] eta = Arr.copy(init);
    	double currentLP = calcUnnormLogprob(eta, counts, etaMean, etaVar);
    	
    	for (int iter=0; iter < maxIter; iter++) {
    		double[] theta = Arr.softmax1(eta);
    		double[] grad = calcEtaGradient(eta, theta, counts, totalCount, etaMean, etaVar);
    		assert ! Arr.isVeryDangerous(grad);
    		    		
//    		double[][] gradCol = new double[grad.length][1];
//    		for (int k=0; k<K; k++) {
//    			gradCol[k][0] = grad[k];
//    		}
//    		
    		// Method 1: naive hessian inversion
//    		double[][] hess = calcEtaHessian(eta, counts, totalCount, etaMean, etaVar);
//    		double[] stepdir1 = new Matrix(hess).inverse().times(new Matrix(gradCol)).getColumnPackedCopy();
//    		Arr.multiplyInPlace(stepdir1, -1);
    		
    		// Method 2: quadratic sherman-morrison inversion
//    		double[][] hessInv = calcInverseHessianViaSM(theta, totalCount, etaVar);
//    		double[] stepdir2 = new Matrix(hessInv).times(new Matrix(gradCol)).getColumnPackedCopy();
    		
    		// Method 3: linear-time SM and gradient at once (eisenstein ICML-2011)
    		// this needs a name. "SM-Newton"?
    		double[] Adiag = new double[K];
    		for (int k=0; k<K; k++) {
    			Adiag[k] = 1.0/(totalCount*theta[k] + 1/etaVar[k]);
    		}
    		// Auv'AG = (Au)(v'AG) = vec*scalar = (A_i u_i)(sum_j v_j A_j G_j)
    		double[] theta1 = Arr.subArray(theta, 0, K);
    		double sum_vAG = Arr.innerProduct(theta1, Adiag, grad);
    		double denom = (1 - totalCount * Arr.innerProduct(Adiag, theta1, theta1));
    		double[] stepdir3 = new double[K];
    		for (int k=0; k<K; k++) {
    			stepdir3[k] = Adiag[k]*grad[k] + totalCount/denom * Adiag[k]*theta[k]*sum_vAG; 
    		}
//    		U.pf("stepdir\t%s\t%s\t%s\n", Arr.sf("%.3f",stepdir1),Arr.sf("%.3f",stepdir2),Arr.sf("%.3f",stepdir3));
//    		assert Arr.L1Norm(Arr.pairwiseSubtract(stepdir1,stepdir2)) < 1e-10;
//    		assert Arr.L1Norm(Arr.pairwiseSubtract(stepdir1,stepdir3)) < 1e-10;
    		double[] stepdir = stepdir3;
    		double scale = 1;
    		double newLP = currentLP;
    		
    		assert !Arr.isVeryDangerous(stepdir) : Arr.toString(stepdir);

    		// dumb: blindly take newton step. has flip-flopping problems.
//    		eta = Arr.pairwiseScaleAdd(eta, stepdir, scale);
    		
    		// line search. this is related to dampened Newton but i think slighly different
    		// at least according to slide 25ish
    		// http://www.cs.cmu.edu/~ggordon/10725-F12/slides/11-matrix-newton-annotated.pdf
    		for (int searchIter=0; searchIter < newtonLinesearchMaxIter; searchIter++) {
        		double[] newEta = Arr.pairwiseScaleAdd(eta, stepdir, scale);
        		newLP = calcUnnormLogprob(newEta, counts, etaMean, etaVar);
        		if (newLP < currentLP) {
//        			U.p("backoff to smaller step");
        			scale *= 0.5;
        		} else {
        			eta = newEta;
        			break;
        		}
    		}
    		double improvement = newLP-currentLP;
    		currentLP=newLP;
//    		U.p("iter "+iter+"\tlogprob " + lnLogprob(eta, counts, etaMean, etaVar)+"\teta\t" + Arr.sf("%.3f",eta));
    		if (Math.abs(improvement/currentLP) < newtonRelativeTol)
    			break;
    	}
    	return eta;
    }
	
	/**
	 * Sample from the Laplace approximation (mode-centered second-order-Gaussian).
	 * Calls the Newton optimizer as a subroutine.
	 */
	public static double[] sampleLaplace(
			final double[] counts, final double totalCount,
			final double[] etaMean, final double[] etaVar,
			FastRandom rand) 
	{
		final int K = etaMean.length;
		double[] etaMode = estNewton(5, new double[K], counts, totalCount, etaMean, etaVar);
		double[][] hess = calcEtaHessian(etaMode, counts, totalCount, etaMean, etaVar);
		Arr.multiplyInPlace(hess, -1);
		return MVNormal2.nextMVNormal(etaMode, Arr.convertToVector(hess), rand);
	}

	/** Diagonal Gaussian approximation.  O(K) time, instead of O(K^2) of the full Gaussian approx. */
	public static double[] sampleLaplaceDiagonal(
			final double[] counts, final double totalCount,
			final double[] etaMean, final double[] etaVar,
			FastRandom rand) 
	{
		double[] etaMode = estNewton(5, new double[etaMean.length], counts, totalCount, etaMean, etaVar);
		return sampleLaplaceDiagonal_givenMode(etaMode, counts, totalCount, etaMean, etaVar, rand);
	}
	
	static double[] sampleLaplaceDiagonal_givenMode(
			double[] etaMode,
			final double[] counts, final double totalCount,
			final double[] etaMean, final double[] etaVar,
			FastRandom rand) 
	{
//		calcEtaGaussianDiagVar
		final int K = etaMode.length; 
		double[] hessDiag =  calcEtaHessianDiagonal(etaMode, counts, totalCount, etaMean, etaVar);
		Arr.multiplyInPlace(hessDiag, -1);
		double[] sample = new double[K];
		for (int k=0; k<K; k++)
			sample[k] = etaMode[k] + rand.nextGaussian(0, 1/hessDiag[k]);
		return sample;
	}
	
//	static Pair<double[],Double> sampleAndLogDensity_LaplaceDiagonal_givenMode(
//			double[] etaMode,
//			final double[] counts, final double totalCount,
//			final double[] etaMean, final double[] etaVar,
//			FastRandom rand) 
//	{
//		final int K = etaMode.length; 
//		double[] hessDiag =  calcEtaHessianDiagonal(etaMode, counts, totalCount, etaMean, etaVar);
//		Arr.multiplyInPlace(hessDiag, -1);
//		double[] sample = new double[K];
//		double logprob = 0;
//		for (int k=0; k<K; k++) {
//			sample[k] = etaMode[k] + rand.nextGaussian(0, 1/hessDiag[k]);
//			logprob = Util.normalLL(sample[k], etaMode[k], 1/hessDiag[k]);
//		}
//		return U.pair(sample, logprob);
//	}
	
	/** sample a multivariate normal that has diagonal covariance */
	public static double[] sampleDiagMV(double[] mean, double[] variances, FastRandom rand) {
		int K = mean.length;
		double[] sample = new double[K];
		for (int k=0; k<K; k++)
			sample[k] = mean[k] + rand.nextGaussian(0, variances[k]);
		return sample;
	}
	
	//////////////////
	
	public static void main(String args[]) {
		double[] counts = new double[]{100, 10, 1, 1, 0,0};
		
		int K = counts.length;
		double[] etaMean = new double[K];
//		double[] etaMean = new double[]{ 0.148, 0.411, 0.364, 0.345, 0.682, -1.185, 0.482, 0.571, 0.542, 0.613};
		double etaVar[] = new double[K]; Arrays.fill(etaVar, 1);
		double totalCount = Arr.sum(counts);

		double[] init = new double[K];
		
//		double[] etaMode;
//		etaMode = LNInference.estNewton(20, init, counts, Arr.sum(counts), etaMean, etaVar);
//		U.pf("etamode newton %s\n", Arr.sf("%.3f",eta));
//		etaMode = LNInference.estBFGS(counts, Arr.sum(counts), etaMean, etaVar);
//		U.pf("etamode bfgs %s\n", Arr.sf("%.3f",eta));
		
		int numSamples = (int) Math.round(Double.valueOf(args[0]));
		int numTrials = Integer.valueOf(args[1]);
		
		for (int trial=0; trial<numTrials; trial++) {
			System.err.print(trial+" ");
			List<double[]> hist = sampleMHLaplaceDiag(counts, totalCount, etaMean, etaVar, numSamples, FastRandom.rand()).history;
//			List<double[]> hist = sampleSliceHistory(numSamples, counts, etaMean, etaVar);
			for (int i=0; i<numSamples; i++) {
				U.pf("%d %d %s\n", trial, i, Arr.sf("%.3f", hist.get(i)));			
			}
		}
		System.err.print("\n");
		
//		List<double[]> hist = laplaceMH_history(counts, totalCount, etaMean, etaVar, numSamples, FastRandom.rand());
////		List<double[]> hist = sampleSliceHistory(numSamples, counts, etaMean, etaVar);
//		for (double[] sample : hist) {
//			U.p(sample);			
//		}

//		for (int itr=0; itr<numSamples; itr++) {
//			U.p(sampleLaplaceDiagonal(counts, totalCount, etaMean, etaVar, FastRandom.rand()));
//		}
		
    }
	
	/**
	 * MH with Laplace approx proposal.
	 * the proposal is not state-dependent: it's always the same mode-centered proposal
	 * This seems to underestimate the tails for variables with high counts
	 * (because the axis-aligned marginal variance isn't a good summary of the full covar matrix then, it seems.)
	 * 
	 * To get a single sample, take last().
	 * suggestion: numSamples=20 for independent draw (well, as indep as you can get)
	 */
	public static MCMC.MHResult sampleMHLaplaceDiag(
			final double[] counts, final double totalCount,
			final double[] etaMean, final double[] etaVar,
			int numSamples,	FastRandom rand) {
		
		Function<double[],Double> targetDensity = new Function<double[],Double>() {
			@Override
			public Double apply(double[] eta) {
				return calcUnnormLogprob(eta, counts, etaMean, etaVar);
			}
		};
		final int K = etaMean.length;
		final double[] etaMode = estNewton(5, new double[K], counts, totalCount, etaMean, etaVar);
		final double[] approxVar = calcEtaLaplaceDiagVar(etaMode, counts, totalCount, etaMean, etaVar);
		Function<double[],double[]> proposer = new Function<double[],double[]>() {
			@Override
			public double[] apply(double[] currentEta_ignored) {
				return sampleDiagMV(etaMode, approxVar, FastRandom.rand());
			}
		};
		MCMC.ProposalDensity proposalDensity = new MCMC.ProposalDensity() {
			@Override
			public double apply(double[] currentState_ignored, double[] proposedState) {
				return Util.normalDiagLL(proposedState, etaMode, approxVar);
			}
		};
		
		MCMC.MHResult r = MCMC.hastings(targetDensity, proposalDensity, proposer, new double[K],
				numSamples, rand);
//		System.err.println("accept rate " + r.acceptRate);
		return r;
	}
	
	/** from an initial state eta, do one MH step: consider a new state, and return whether it's accepted.
	 * returns (WasItAccepted, NewValue)
	 * where NewValue is 'null' if not accepted.
	 * (for Hoff 2003 approach)
	 */
	public static OneMH sampleOneMH(double[] oldEta,
			final double[] counts, final double totalCount,
			final double[] etaMean, final double[] etaVar,
			FastRandom rand)
	{
		// Construct the Laplace-diag proposal
		final int K = etaMean.length;
		final double[] etaMode = estNewton(5, new double[K], counts, totalCount, etaMean, etaVar);
		final double[] approxVar = calcEtaLaplaceDiagVar(etaMode, counts, totalCount, etaMean, etaVar);
		// Take the proposal and consider it.
		// q(x|.) doesn't depend on RHS, so it's really just q(x) versus q(x')
		double[] newEta = Util.normalDiagSample(etaMode, approxVar, rand);
		double lq_new = Util.normalDiagLL(newEta, etaMode, approxVar);
		double lq_old = Util.normalDiagLL(oldEta, etaMode, approxVar);
		double lp_new = calcUnnormLogprob(newEta, counts, etaMean, etaVar);
		double lp_old = calcUnnormLogprob(oldEta, counts, etaMean, etaVar);
		// NOTE could speed up last calculation by passing in the precomputed old theta
		
		double lalpha = lp_new-lp_old + lq_old-lq_new;
		if (lalpha >= 0 || rand.nextUniform() < Math.exp(lalpha)) {
			return new OneMH(true, newEta);
		} else {
			return new OneMH(false, null);
		}
	}
	
	public static class OneMH {
		public boolean wasAccepted;
		public double[] newValue;
		public OneMH(boolean a, double[] d) { wasAccepted=a; newValue=d; }
	}
	
	static void oldTestStuff() {
//		double[] counts = new double[]{ 5,1 };
//		final double[] counts = new double[]{
//				100, 10, 3, 1, 1, 0,0,0,0,0,
//				100, 10, 3, 1, 1, 0,0,0,0,0,
//				100, 10, 3, 1, 1, 0,0,0,0,0,
//				100, 10, 3, 1, 1, 0,0,0,0,0,
//				100, 10, 3, 1, 1, 0,0,0,0,0
//		};

//		int nouter = 50*1000;
//		int t0 = (int) System.currentTimeMillis();
//		for (int outer=0; outer<nouter; outer++) {
//			if (outer % 10000==0) U.pf(".");
////			LNInference.estNewton(5, init, counts, sum, etaMean, etaVar);
////			ContextModel.lnMAPBFGS(counts, Arr.sum(counts), etaMean, etaVar);
//
//		}
//		int elapsed = (int) System.currentTimeMillis() - t0;
//		U.pf("\n%g ms per iter\n", elapsed*1.0 / nouter);
	}
	

}

package util;

import util.Jama.Matrix;
import util.Jama.QRDecomposition;

/**
 * various bayesian inference procedures for conjugate prior linear gaussian models
 * intended to support Gibbs samplers
 * - Scalar mean, var
 * - Linear Regression
 * - Stationary 1d LDS
 * 
 */
public class GaussianInference {
	/**
	 * Sample a scalar variance from conjugate posterior, under scaled inverse chi-sq prior (i.e. inv wishart)
	 * @param sumSqDev: sum (x_i - xbar)^2  (= [sum x_i^2] - [sum x_i]^2 )
	 * @param priorVar: sigma_0^2 in Murphy 4.6.2.2 notation (prior value)
	 * @param priorStrength: nu_0 in Murphy 4.6.2.2 notation (pseudocount for prior value)
	 */
	public static double samplePosteriorVariance(double sumSqDev, int N, double priorValue, double priorStrength, FastRandom rand) {
		double postStrength = priorStrength + N;
		double postVar = (priorValue*priorStrength + sumSqDev) / postStrength;
//		U.pf("postVar=%g postStrength=%g\n", postVar, postStrength);
		
		// Murphy uses rate parameterization
		double shape = postStrength / 2;
		double rate = postStrength*postVar / 2;
		// Mallet's gamma implementation uses the scale parameterization
		// and we return the inverse because we want an inverse gamma sample
		return 1 / rand.nextGamma(shape, 1/rate);
		// I always get very confused about this, esp since the inverse gamma has its parameters look inverted again compared to the gamma.
		// Only safe thing to do is look at histograms per parameter
	}
	
	/** "center" is mode or mean..? */
	public static double sampleInvChisq(double center, double strength, FastRandom rand) {
		double shape = strength / 2;
		double rate = strength*center / 2;
		return 1 / rand.nextGamma(shape, 1/rate);
	}
	
	public static double samplePosteriorVariance(double[] data, double priorValue, double priorStrength, FastRandom rand) {
		double sumSq = Arr.sumSquaredError(data);
		return samplePosteriorVariance(sumSq, data.length, priorValue, priorStrength, rand);
	}
	
	/**
	 * Sample a normal scalar mean from conjugate posterior, under normal prior, and known emission variance.
	 * Murphy 4.4.2.1
	 * 
	 * @param emissionVar: known variance of emissions
	 * @param priorMean: mean of mu prior dist
	 * @param priorVar: variance of mu prior dist
	 */
	public static double samplePosteriorMean(double dataMean, int N, double emissionVar, double priorMean, double priorVar, FastRandom rand) {
		double priorPrec = 1/priorVar;
		double emitPrec = 1/emissionVar;
		double Z = N*emitPrec+priorPrec;
		double postMean = (N*emitPrec)/Z * dataMean + priorPrec/Z * priorMean;
		double postVar = 1/(priorPrec + N*emitPrec);
//		U.pf("postMean=%g postVar=%g\n", postMean, postVar);
		return rand.nextGaussian(postMean, postVar);
	}
	/**
	 * Sample a normal scalar mean from conjugate posterior, under normal prior, and known emission variance.
	 * Murphy 4.4.2.1
	 */ 
	public static double samplePosteriorMean(double[] data, double emissionVar, double priorMean, double priorVar, FastRandom rand) {
		double mean = Arr.mean(data);
		return samplePosteriorMean(mean, data.length, emissionVar,priorMean,priorVar,rand);
	}
	
	/**
	 * normal scalar mean under heteroskedastic known emission variances
	 * has been checked it gives same answers as unweighted version, but weighting hasn't been tested yet
	 */
	public static double samplePosteriorMean(double[] data, double[] emissionVars, double priorMean, double priorVar, FastRandom rand) {
		double[] emitPrecs = Arr.copy(emissionVars);
		Arr.powInPlace(emitPrecs, -1);
		OnlineNormal1dWeighted rv = new OnlineNormal1dWeighted();  // overkill, could be made faster
		for (int i=0; i < data.length; i++) {
			rv.add(data[i], emitPrecs[i]);
		}
		double dataMean = rv.mean();
		double priorPrec = 1/priorVar;
		double wsum = Arr.sum(emitPrecs);
		double Z = wsum + priorPrec;
		double postMean = wsum/Z * dataMean  +  priorPrec/wsum * priorMean;
		double postVar  = 1/(priorPrec + wsum);
		return rand.nextGaussian(postMean, postVar);
	}


	static void testPosteriorVariance(String args[]) {
		// Test prior strength
		// > plot(density(scan(pipe("./java.sh GaussianInference 10 1e-3 1 2 3"))),xlim=c(0,20))
		// > plot(density(scan(pipe("./java.sh GaussianInference 10 1 1 2 3"))),xlim=c(0,20))
		// > plot(density(scan(pipe("./java.sh GaussianInference 10 100 1 2 3"))),xlim=c(0,20))
		// Test adding more data
		// > plot(density(scan(pipe("./java.sh GaussianInference 10 1 1 2 3 1 2 3"))),xlim=c(0,20))
		FastRandom rand = new FastRandom();
		double[] vals = new double[args.length-2];
		double priorVar = Double.valueOf(args[0]);
		double priorStrength=Double.valueOf(args[1]);
		for (int k=0; k<args.length-2; k++) vals[k] = Double.valueOf(args[k+2]);
		for (int i=0; i < 10000; i++) {
			U.p(samplePosteriorVariance(vals, priorVar, priorStrength, rand));
		}
	}
	
	static void testPosteriorMean(String args[]) {
		FastRandom rand = new FastRandom();
		double[] vals = new double[args.length-2];
		double emitVar 	 = Double.valueOf(args[0]);
		double priorMean = Double.valueOf(args[1]);
		double priorVar  = Double.valueOf(args[2]);
		for (int k=0; k<args.length-3; k++) vals[k] = Double.valueOf(args[k+3]);
		double[] emitVars = Arr.rep(emitVar, vals.length);
		for (int i=0; i < 100000; i++) {
			U.p(samplePosteriorMean(Arr.mean(vals), vals.length, emitVar, priorMean, priorVar, rand));
//			U.p(samplePosteriorMean(vals, emitVars, priorMean, priorVar, rand));
		}
	}
	
	
	////////////////////////////

	public static class MVNormalParams {
		public double[] mean;
		public double[][] var;
		public double[][] prec;
		
		public String toString() {
			return String.format(
					"postMean %s\npostVar %s\npostPrec %s\n",
					Arr.sf("%g", mean), 
					var==null ? "null" : Arr.sf("%g", Arr.convertToVector(var)),
					prec==null ? "null" : Arr.sf("%g", Arr.convertToVector(prec)));
		}
	}
	
	/** 
	 * Compute conjugate normal posterior for linreg coefs.
	 * Calculate both posterior variance and precision.  If you need only one, get the decomp instead.
	 */
	public static MVNormalParams estPosteriorLinregCoefs(
			double[][] X, double[] Y, double noiseVar, 
			double[] priorMean, double[][] priorPrec)
	{

		// Naive literal implementation
//		Matrix priorPrec_ = new Matrix(priorPrec);
//		Matrix priorMean_ = new Matrix(priorMean, priorMean.length);
//		Matrix X_ = new Matrix(X);
//		Matrix Y_ = new Matrix(Y, Y.length);		
//		Matrix XtX = X_.transpose().times(X_);
//		Matrix XtY = X_.transpose().times(Y_);
//		Matrix postPrec= priorPrec_.plus(XtX.times(1/noiseVar));
//		Matrix postVar = postPrec.inverse();
//		Matrix postMean = postVar.times( 
//				priorPrec_.times(priorMean_) .plus( XtY.times(1/noiseVar) ));

		// Implementation via QR and Cholesky decompositions.  PMTK normalEqnBayes.m, Murphy 7.5.2
//		[Lam0root] = chol(Lam0);
//		% use pseudo data trick
//		Xtilde = [X/sigma; Lam0root];
//		ytilde = [y/sigma; Lam0root*w0];
//		[Q,R] = qr(Xtilde, 0);
//		wn = R\(Q'*ytilde);
//		if nargout >= 2
//		  Rinv = inv(R);
//		  Sn = Rinv*Rinv';
//		end
		
		LinregDecomp d = estPosteriorLinregDecomp(X, Y, noiseVar, priorMean, priorPrec);
		Matrix R = d.qr.getR();
		Matrix Rinv = R.inverse(); // TODO can be done more cheaply since R is upper triangular
		Matrix postVar = Rinv.times(Rinv.transpose());
		Matrix postPrec= R.transpose().times(R);
		
		MVNormalParams param = new MVNormalParams();
		param.mean = d.mean;
		param.var  = postVar.getArray();
		param.prec = postPrec.getArray();
		return param;
	}
	
	/**
	 * Represents a linreg coef MVN posterior,
	 * in the first stage of computation: right after running the QR decomposition.
	 * The R matrix is the coefs' precision root.  R^-1 is variance root.
	 */
	public static class LinregDecomp {
		public QRDecomposition qr; // QR decomp of X
		public double[] mean; 	// coefs' posterior mean
		
		/** draw one coef sample from this MVN posterior. */
		public double[] sample(FastRandom rand) {
			return MVNormal2.nextMVNormalWithCholesky(
					mean,
					qr.getR().getColumnPackedCopy(),
					rand);
		}
		
		/** posterior precision of the coefs. */
		public double[][] precision() {
			Matrix R = qr.getR();
			return R.transpose().times(R).getArray();
		}
	}
	
	/** lower-level routine: get the relevant QR decomposition for this bayes linreg. */
	public static LinregDecomp estPosteriorLinregDecomp(
			double[][] X, double[] Y, double noiseVar, 
			double[] priorMean, double[][] priorPrec)
	{
		LinregDecomp ret = new LinregDecomp();
		Matrix priorPrec_ = new Matrix(priorPrec);
		Matrix priorMean_ = new Matrix(priorMean, priorMean.length);
		Matrix L = priorPrec_.chol().getL();
		double noiseSd = Math.sqrt(noiseVar);
		double[][] Xtilde = Arr.rbind(Arr.multiply(X, 1/noiseSd), L.getArray());
		double[] Ytilde = Arr.concat(Arr.multiply(Y, 1/noiseSd), L.times(priorMean_).getRowPackedCopy());
		
		QRDecomposition qr = new QRDecomposition(new Matrix(Xtilde));
		ret.mean = qr.solve(new Matrix(Ytilde, Ytilde.length)).getRowPackedCopy();
		ret.qr = qr;
		return ret;
	}

	/** noiseVars allows different noise terms, though independent. */
	public static LinregDecomp estPosteriorLinregDecomp(
			double[][] X, double[] Y, double[] noiseVars, 
			double[] priorMean, double[][] priorPrec)
	{
		LinregDecomp ret = new LinregDecomp();
		Matrix priorPrec_ = new Matrix(priorPrec);
		Matrix priorMean_ = new Matrix(priorMean, priorMean.length);
		Matrix L = priorPrec_.chol().getL();
		
		// Gelman BDA (14.7) calls the heteroskedastic covariance matrix "Q"
		// inversion is easy since diagonal
		double[][] Xtilde1= Arr.copy(X);
		for (int i=0; i<X.length; i++) {
			Arr.multiplyInPlace(Xtilde1[i], 1/Math.sqrt(noiseVars[i]));
		}
		double[][] Xtilde = Arr.rbind(Xtilde1, L.getArray());
		double[] Ytilde1 = Arr.pairwiseMultiply(Y, Arr.pow(noiseVars, -0.5));
		double[] Ytilde = Arr.concat(Ytilde1, L.times(priorMean_).getRowPackedCopy());
		
		QRDecomposition qr = new QRDecomposition(new Matrix(Xtilde));
		ret.mean = qr.solve(new Matrix(Ytilde, Ytilde.length)).getRowPackedCopy();
		ret.qr = qr;
		return ret;
	}

	/** coef prior is in PRECISION (not variance!) */ 
	public static double[] samplePosteriorLinregCoefs(
			double[][] X, double[] Y, double noiseVar, 
			double priorMean, double priorPrec,
			FastRandom random
	) {
		int J = X[0].length;
		double[][] _priorPrec = Arr.diag(Arr.rep(priorPrec, J));
		double[] _priorMean = Arr.rep(priorMean, J);
		return samplePosteriorLinregCoefs(X,Y,noiseVar,_priorMean,_priorPrec, random);
	}

	
	/** Sample a linreg coefficient vector from its conjugate posterior */
	public static double[] samplePosteriorLinregCoefs(
			double[][] X, double[] Y, double noiseVar, 
			double[] priorMean, double[][] priorPrec,
			FastRandom random
	) {
		// This works but is computationally wasteful.
//		MVNormalParams posterior = estPosteriorLinregCoefs(X,Y,noiseVar,priorMean,priorPrec);
//		return MVNormal2.nextMVNormal(posterior.mean, Arr.convertToVector(posterior.prec), random);
		
		// Better: directly use the QR decomposition to get at the precision root.
		LinregDecomp d = estPosteriorLinregDecomp(X, Y, noiseVar, priorMean, priorPrec);
		
		// R is the cholesky root of precision.  R'R = posterior precision. 
		// Jama's QR decomp gives R as upper triangular.
		// Mallet's sampling function wants lower triangular.
		// we can do a cheap transpose by asking for a column-packed copy; Mallet interprets as row-packed.
		// would be better to rewrite the mallet routines for this
		return MVNormal2.nextMVNormalWithCholesky(
				d.mean,
				d.qr.getR().getColumnPackedCopy(),
				random);
	}
	
	public static double[] samplePosteriorLinregCoefs(
			double[][] X, double[] Y, double[] noiseVars, 
			double[] priorMean, double[][] priorPrec,
			FastRandom random
	) {
		LinregDecomp d = estPosteriorLinregDecomp(X, Y, noiseVars, priorMean, priorPrec);
		return MVNormal2.nextMVNormalWithCholesky(
				d.mean,
				d.qr.getR().getColumnPackedCopy(),
				random);
	}

	static void testLinreg2(String args[]) {
		double[][] X = Arr.readDoubleMatrix(args[0]);
		double[] Y = Arr.readDoubleVector(args[1]);
		int J = X[0].length;
		LinregDecomp d = estPosteriorLinregDecomp(X, Y, 1,
				Arr.rep(0, J), Arr.diag(Arr.rep(1, J)));
		for (int i=0; i<1000; i++) {
			double[] beta = d.sample(FastRandom.rand());
			U.p("beta " + Arr.sf("%.3g", beta));
		}
	}
	
	static void testLinreg1(String args[]) {
		FastRandom rand = new FastRandom();
		
		double[][] X = new double[][]{{1,1}, {1,1}, {1,1}};
		int J = X[0].length;
		double[]   Y = new double[]{ 2 , 6, 5};
		double[] pm = new double[J];
//		double[][] pp = Arr.diag(Arr.rep(10, J));
		double[][] pp = Arr.diag(new double[]{1,2});
		
		int numIter = Integer.valueOf(args[0]);
		
		MVNormalParams p = estPosteriorLinregCoefs(X, Y, 1, pm,pp);
		U.p(p);
		for (int i=0; i < numIter; i++) {
			double[] samp = samplePosteriorLinregCoefs(X,Y, 1, pm, pp, rand);
			U.p(samp);
		}
		
		// Sanity test: does the samples' covariance agree with inferred covariance?
		// > d=read.table(pipe("cd .. && ./java.sh GaussianInference 5000 | grep -v post|cleanarr")); cov(d)
	}
	
	/* Linear-Gaussian State Space Models aka DLM aka LDS
	 * 
	 *   z    ->  z    ->  z
	 *    \>y      \>y      \>y
	 *     
	 * Inference problems. We want params for () or a sample of {} while conditioning on [].
	 *                   
	 *  Prediction   Forecast     Filter     Smoother   Joint Sample (FFBS)
 	 *   z z (z)      z z  z      z z (z)    z (z) z    {z z z}
	 *  [y y] y      [y y](y)    [y y  y]   [y  y  y]   [y y y]
	 *
	 * Murphy      West&Harrison
	 * mu_t|t-1    a_t           P(z_t | y_1:t-1) = Predicted state mean
	 *  S_t|t-1    R_t                                              var
	 * yhat_t      f_t           P(y_t | y_1:t-1) = One-step forecast mean
	 *             Q_t                                                var
	 * mu_t        m_t           P(z_t | y_1:t)   = Filtered state mean
	 *  S_t        C_t                                             var
	 *  
	 * Martin&Quinn use the West&Harrison notation
	 */
	
	/**
	 * P(z_t | y_1:t) ~ N(filterMeans[t], filterVars[t])  (mu_t, S_t)
	 * P(z_t | y_1:t-1) ~ N(predMeans[t], predVars[t])    (mu_t|t-1, S_t|t-1)
	 */
	public static class FilterResult1d {
		double[] filterMeans;
		double[] filterVars;
		double[] predMeans;
		double[] predVars;
		
		public FilterResult1d(int T) {
			filterMeans = new double[T];
			filterVars = new double[T];
			predMeans = new double[T];
			predVars = new double[T];
		}
	}
	
	/**
	 * Kalman Filter for 1d states and emissions, with missing value handling.
	 * In Murphy notation (ch 18),
	 * 
	 * z_t = A z_t-1 + Bu + N(Q)        <== system dynamics
	 * y_t = C z_t   + Du + N(R)        <== observation emissions
	 * 
	 * A: transCoef (scalar)
	 * Q: transVar  (scalar)
	 * C: emitCoef  (scalar)
	 * R: emitVar   (scalar)
	 * Bu: transIntercepts  (vector size T)
	 * Du: emitIntercepts   (vector size T)
	 * 
	 * and z_0 ~ N(priorMean, priorVar)
	 * 
	 * Missing value handling (flagged as Double.POSITIVE_INFINITY in data[]):
	 * Treat as if there is no observation on those timesteps, so the filtered mean/var
	 * is based on just the prior predicted mean/var.
	 */
	public static FilterResult1d kalmanFilter(
			double[] data,                     // observations 
			double transCoef, double transVar, // system dynamics 
			double emitCoef, double emitVar,   // observation emissions 
			double[] transIntercepts, double[] emitIntercepts, // per-timestep constants
			double priorMean, double priorVar // priors on first timestep's state
	) {
		int T = data.length;
		FilterResult1d result = new FilterResult1d(T);

		for (int t=0; t<T; t++) {
			// Prediction step (get the prior): mu_t|t-1, S_t|t-1
			if (t==0) {
				result.predMeans[t] = priorMean + transIntercepts[t];
				result.predVars[t] = priorVar;
			} else {
				result.predMeans[t] = transCoef * result.filterMeans[t-1] + transIntercepts[t];
				result.predVars[t]  = transCoef*transCoef*result.filterVars[t-1] + transVar;				
			}
			if (data[t] == Double.POSITIVE_INFINITY) {
				// missing value: posterior variance simply widens
				result.filterMeans[t] = result.predMeans[t];
				result.filterVars[t] = result.predVars[t];
			} else {
				// Measurement step (forecast and correction): yhat_t, K_t
				double yForecast = emitCoef * result.predMeans[t] + emitIntercepts[t];
				double C = emitCoef, H = result.predVars[t], R = emitVar;
				double gain = H*C / (C*C*H + R);
				double resid = data[t] - yForecast;
				// mu_t, S_t
				result.filterMeans[t] = result.predMeans[t] + gain*resid;
				result.filterVars[t]  = result.predVars[t] * (1 - gain*C);
			}
		}
		return result;
	}
	
	/**
	 * Kalman Filter for 1d state, multivariate observations, and diagonal covariance emissions.
	 * 
	 * z_t = A z_t-1 + Bu + N(Q)        <== system dynamics
	 * y_t = C z_t   + Du + N(R)        <== observation emissions
	 * 
	 * A: transCoef  (scalar)
	 * Q: transVar   (scalar)
	 * C: emitCoef   (vector dim K)
	 * R: emitVar    (vector dim K, represents KxK diagonal)
	 * Bu: transIntercepts  (vector dim T)
	 * Du: emitIntercepts   (matrix dim TxK)
	 * 
	 * and z_0 ~ N(priorMean, priorVar)
	 *
	 * Missing value handling (flagged as Double.POSITIVE_INFINITY in data[]):
	 * Treat as if there is no observation on those timesteps, so the filtered mean/var
	 * is based on just the prior predicted mean/var.
	 */
	public static FilterResult1d kalmanFilter(
			double[][] data,                   // observations, dim T x K 
			double transCoef, double transVar, // system dynamics 
			double[] emitCoef, double[] emitVar,   // observation emissions 
			double[] transIntercepts, // dim T 
			double[][] emitIntercepts, // dim T x K
			double priorMean, double priorVar // priors on first timestep's state
	) {
		int T = data.length;
		int K = data[0].length;
		assert emitIntercepts.length==T && transIntercepts.length==T;
		assert emitCoef.length==K && emitVar.length==K && emitIntercepts[0].length==K;

		FilterResult1d result = new FilterResult1d(T);

		for (int t=0; t<T; t++) {
			// Prediction step (get the prior): mu_t|t-1, S_t|t-1
			if (t==0) {
				result.predMeans[t] = priorMean + transIntercepts[t];
				result.predVars[t] = priorVar;
			} else {
				result.predMeans[t] = transCoef * result.filterMeans[t-1] + transIntercepts[t];
				result.predVars[t]  = transCoef*transCoef*result.filterVars[t-1] + transVar;				
			}
			if (data[t][0] == Double.POSITIVE_INFINITY) {
				// missing value: no measurement correction. posterior variance simply widens.
				// (slightly risky convention. should check if all data[t] are infinity.)
				result.filterMeans[t] = result.predMeans[t];
				result.filterVars[t] = result.predVars[t];
			} else {
				// Measurement step (forecast and correction): yhat_t, K_t
				final double C[] = emitCoef, H = result.predVars[t], R[] = emitVar;
				double[] C_mu = Arr.multiply(emitCoef, result.predMeans[t]);
				double[] yForecast = Arr.pairwiseAdd(C_mu, emitIntercepts[t]);
				double[] resid = Arr.pairwiseSubtract(data[t], yForecast);
				
				// HC'[CHC'+R]^-1 = (HC)'[(HC)C'+R]^-1 for one-dim latent state
				double[] HC = Arr.multiply(C, H);
				double[] Rinv = Arr.pow(R, -1);
				double[] gain = Util.diagSMLeftMultiply(HC, Rinv, HC, C); 
				
				// mu_t, S_t
				result.filterMeans[t] = result.predMeans[t] + Arr.innerProduct(gain, resid);
				double KC = Arr.innerProduct(gain, C);
				result.filterVars[t]  = result.predVars[t] * (1 - KC);
			}
		}
		return result;
	}
	/** the BS of FFBS */
	public static double[] backwardSample(FilterResult1d result, double transCoef, FastRandom rand) {
		// (FFBS originally from Carter&Kohn 1994, Fruhwirth-Schnatter 1994)
		// Murphy doesn't cover FFBS, though it's just a tiny tweak on RTS smoothing.
		// West&Harrison 15.2.3 notation below. if no subscript, it's actually _t  
		// P(z_t | z_t+1, y_1:T) ~ N(h, H)
		// h = m + B(theta_t+1 - a_t+1)    [West]
		// h = mu_t + B(z_t+1 - mu_t+1|t)  [Murphy]
		// H = C - B R_t+1 B'              [West]
		// H = S_t - B S_t+1|t B'          [Murphy]
		// B = C G'_t+1 R^-1_t+1           [West]
		// B = S_t A'_t+1 S^-1_t+1|t       [Murphy]
		int T = result.filterMeans.length;
		double[] sample = new double[T];
		sample[T-1] = rand.nextGaussian(result.filterMeans[T-1], result.filterVars[T-1]);
		for (int t=T-2; t>=0; t--) {
			double B = result.filterVars[t] * transCoef / result.predVars[t+1];
			double h = result.filterMeans[t] + B*(sample[t+1] - result.predMeans[t+1]);
			double H = result.filterVars[t]  - B*B*result.predVars[t+1];
			sample[t] = rand.nextGaussian(h, H);
		}
		return sample;
	}
	
	static void testKF1(String[] args) {
		double numSamples = Double.valueOf(args[0]);
		double transVar = Double.valueOf(args[1]);
		double emitVar = Double.valueOf(args[2]);
		double transDrift = Double.valueOf(args[3]);
		int T = args.length - 4;
		double[] data = new double[T];
		for (int i=0; i<T; i++)
			data[i] = Double.valueOf(args[i+4]);
		double[] transI = new double[T];
		assert transDrift==0;
//		Arrays.fill(transI, transDrift);
		double[] emitI = new double[T];
//		FilterResult1d r = kalmanFilter(data, 1,transVar, 1, emitVar, transI, emitI, 0, 100);
		
		int K=1;
		double[][] data2 = new double[T][K];
		for (int t=0; t<T; t++) { data2[t][0] = data[t]; }
		FilterResult1d r = kalmanFilter(data2, 1,transVar, Arr.rep(1,K), Arr.rep(emitVar,K), new double[T], new double[T][K], 0,100);
		
		
		U.p(r.filterMeans);
		U.p(r.filterVars);
		FastRandom rand = new FastRandom();
		for (int i=0; i<numSamples; i++)
			U.p(backwardSample(r, 1, rand));
	}
	
	static void testKF2(String[] args) {
		double numSamples = Double.valueOf(args[0]);
		int K = 3;
		int T = (args.length - 1) / K;
		double[][] data = new double[T][K];
		for (int t=0; t<T; t++)
			for (int k=0; k<K; k++)
				data[t][k] = Double.valueOf(args[k*T+t+1]);

		double[] emitCoef = new double[]{ -.7, .3, .7 };
		double[] emitVar = new double[]{ 10, 10, 10 };
		FilterResult1d r = kalmanFilter(data, 1,1, emitCoef,emitVar,
				new double[T], new double[T][K], 0,100);
		
		U.pf("filtermean ");U.p(r.filterMeans);
		U.pf("filtervar "); U.p(r.filterVars);
		FastRandom rand = new FastRandom();
		for (int i=0; i<numSamples; i++) {
			U.pf("sample "); U.p(backwardSample(r, 1, rand));
		}
	}
	
	
	public static void main(String args[]) {
		testLinreg2(args);
//		testKF2(args);
//		testLinreg(args);
//		testPosteriorVariance(args);
//		testPosteriorMean(args);
	}
}

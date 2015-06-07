package util;
import it.unimi.dsi.fastutil.doubles.DoubleArrays;

import java.util.*;

import org.apache.commons.math3.distribution.NormalDistribution;

import com.google.common.collect.*;

import edu.stanford.nlp.math.SloppyMath;

/**
 * Stats and math utils
 * 
 * Basic things involving arrays and matrixes should instead go into Arr.
 * 
 * Naming convention for gaussians:
 *   'normal'     ==> univariate normal
 *   'normalMV'   ==> multivariate normal
 *   'normalDiag' ==> multivariate normal with diagonal covariance
 *   
 **/
public class Util {

	/** return x \in R.  use scale=1 */
	public static double logitSampleLeftTrunc(double lowerBound, double logisticMean) {
		double lowQuantile = SloppyMath.sigmoid(lowerBound - logisticMean);
		double u = FastRandom.rand().nextUniform(lowQuantile, 1);
		return logisticMean + Math.log(u/(1-u));
	}
	/** return x \in R.  use scale=1 */
	public static double logitSampleRightTrunc(double upperBound, double logisticMean) {
		double highQuantile = SloppyMath.sigmoid(upperBound - logisticMean);
		double u = FastRandom.rand().nextUniform(0, highQuantile);
		return logisticMean + Math.log(u/(1-u));
	}
	public static double normalSampleLeftTrunc(double lowerBound, double mean, double var) {
		NormalDistribution d = new NormalDistribution(mean, Math.sqrt(var));
		double lowerBoundQuantile = d.cumulativeProbability(lowerBound); 
		double u = FastRandom.rand().nextUniform(lowerBoundQuantile, 1);
		return d.inverseCumulativeProbability(u);
	}
	public static double normalSampleRightTrunc(double upperBound, double mean, double var) {
		NormalDistribution d = new NormalDistribution(mean, Math.sqrt(var));
		double upperBoundQuantile = d.cumulativeProbability(upperBound);
		double u = FastRandom.rand().nextUniform(0, upperBoundQuantile);
		return d.inverseCumulativeProbability(u);
	}
	
//	public static void main(String[] args) {
//		for (int i=0; i<10000; i++) {
//			double x = normalSampleRightTrunc(Double.valueOf(args[0]), Double.valueOf(args[1]), Double.valueOf(args[2]));
//			U.p(x);
//		}
//	}
	
	/** unnormalized log-prob (no partition)
	 * WARNING takes the PRECISION matrix (inverse covariance) */
	public static double normalMVLL_unnorm(double[] x, double[] mean, double[][] prec) {
		int D = mean.length;
		assert D>0 && D==prec.length && D==prec[0].length;
		// concordance/discordance metaphor from smolensky or one of those 80's neural network people
		double discordance = 0;
		for (int i=0; i<D; i++) {
			for (int j=0; j<D; j++) {
				discordance += prec[i][j] * (x[i]-mean[i]) * (x[j]-mean[j]);
			}
		}
		assert discordance >= 0 : "precision matrix not positive semidefinite!";
		return discordance * -0.5;
	}
	
	/** univariate normal density N(x | mean, var) */
	public static double normalLL(double x, double mean, double var) {
		double diff = x-mean;
		return -Math.log(Math.sqrt(2*Math.PI * var)) - (0.5/var) * diff*diff;
	}
	/** multivariate normal density under diagonal covariance */
	public static double normalDiagLL(double[] x, double[] mean, double[] vars) {
		double lp = 0;
		for (int k=0; k < mean.length; k++) {
			lp += normalLL(x[k], mean[k], vars[k]);
		}
		return lp;
	}
	
	/**
	 * NOTE this is DIFFERENT than MVNormal in that you give the VARIANCE!  NOT THE PRECISION!
	 * specifically, the diagonal of the covar matrix.
	 */
	public static double[] normalDiagSample(double[] mean, double[] vars, FastRandom rand) {
		double[] ret = new double[mean.length];
		for (int k=0; k < mean.length; k++) {
			ret[k] = rand.nextGaussian(mean[k], vars[k]);
		}
		return ret;
	}

//	public static void main(String[] args) {
//		U.p(normalLL(0,0,1));
//		U.p(normalLL(3,0,1));
//		U.p(normalLL(0,0,0.01));
//		U.p(normalLL(3,0,10));
//		
//		util.Timer.timer().tick();
//		for (int i=0; i<1e9; i++) {
//			normalLL(3.5, 0, 1);
//		}
//		util.Timer.timer().tockPrint();
//	}

	/**
	 * Diagonalized Sherman-Morrison inverse
	 * 
	 * computes (A + uv')^{-1} where A is diagonal.  Quadratic time.
	 * 
	 * http://en.wikipedia.org/wiki/Sherman%E2%80%93Morrison_formula
	 * http://mathworld.wolfram.com/Sherman-MorrisonFormula.html
	 * http://www.cs.cornell.edu/~bindel/class/cs6210-f09/lec12.pdf
	 * 
	 * This takes A^-1 as input!
	 */
	public static double[][] diagSM(double[] Ainv, double[] u, double[] v) {
		assert Ainv.length==u.length;
		assert Ainv.length==v.length;
		final int n = u.length;
		
		// (A + uv')^-1 = A^-1 - NumeratorVec / DenomScalar
		
		double denom = 1;
		for (int i=0; i<n; i++) {
			denom += Ainv[i]*u[i]*v[i];
		}
		if (Math.abs(denom) < 1e-100) {
			System.err.println("WARNING Sherman-Morrison looks unstable");
			if (denom==0) denom=1e-100;
		}
		
		double[][] ret = new double[n][n];
		for (int i=0; i<n; i++) {
			for (int j=0; j<n; j++) {
				if (i==j) ret[i][j] += Ainv[i];
				double top = Ainv[i]*Ainv[j]*u[i]*v[j];
				ret[i][j] -= top/denom;
			}
		}
		return ret;
	}
	/*
	 * x vector, A diagonal matrix
	 * matrix operations that only create diagonal matrixes or vectors
	 * can be represented in terms of vector representations
	 * using operators: . inner product, * Hadamard
	 * There must be a formal real name for this
	 * 
	 * Matrix  Vectors      Matrix output
	 *  x'y     x.y      => scalar
	 *  AB      A*B      => diag
	 *  x'A     x*A      => diag
	 *  Ax      A*x      => vector
	 *  x'Ay    x.A.y    => scalar
	 *  x'ABy   x.A.B.y  => scalar   
	 *  
	 *  The vector system conflates diagonal versus vector output.
	 *  Note also
	 *  x.A.y = (x*A).y = x.(A*y)
	 *  x.A.B.y = (x*A*B).y = x.(A*B*y) = (A*x*y).B
	 */
	
	/**
	 * Calculate in linear time
	 *   w'(A + uv')^-1 
	 * where A is diagonal, and u,v,w vectors.  Note, pass in A^-1 as vector.
	 * 
	 * Applying the Sherman-Morrison formula and reordering to linear-time operations,
	 * = w'(Ainv - 1/[1+v'Ainv u] (Ainv u v' Ainv))
	 * = w'Ainv - 1/[1+v'Ainv u]  w' (Ainv u v' Ainv) 
	 * = w'Ainv - 1/[1+v'Ainv u] [w' Ainv u] (v' Ainv)
	 * = w*Ainv - 1/[1+v.Ainv.u] [w.Ainv.u] (v*Ainv)
	 * Last line in vector notation with  . inner product, * Hadamard.
	 */
	public static double[] diagSMLeftMultiply(double[] w, double[] Ainv, double[] u, double[] v) {
		int N = w.length;
		double c = Arr.innerProduct(w,Ainv,u) / (1 + Arr.innerProduct(v, Ainv, u));
		double[] ret = new double[w.length];
		for (int i=0; i<N; i++)
			ret[i] = w[i]*Ainv[i] - c*v[i]*Ainv[i];
		return ret;
	}
	
//	public static void main(String[] args) {
//		/* compare in R:
//		   f=function(n) rcauchy(n,scale=100)
//		   w=f(K); A=f(K); u=f(K); v=f(K); list(w,A,u,v)
//		   write(w,"tmp.w",1);write(A,"tmp.A",1);write(u,"tmp.u",1);write(v,"tmp.v",1)
//		   print(t(w) %*% solve(diag(A) + u %*% t(v)))
//		   system("./java.sh Util tmp.w tmp.A tmp.u tmp.v")
//		 */
//		double[] w = Arr.readDoubleVector(args[0]);
//		double[] A = Arr.readDoubleVector(args[1]);
//		double[] Ainv = Arr.pow(A, -1);
//		double[] u = Arr.readDoubleVector(args[2]);
//		double[] v = Arr.readDoubleVector(args[3]);
//		U.p(diagSMLeftMultiply(w,Ainv,u,v));
//	}
	
	/**
	 * Calculate in linear time
	 *   (A + uv')^-1 w 
	 * where A is diagonal, and u,v,w vectors.  Note, pass in A^-1 as vector.
	 * This follows Eisenstein et al ICML-2011, Eq (6).
	 * 
	 * Applying the Sherman-Morrison formula and reordering to linear-time operations,
	 * = (Ainv - 1/[1+v'Ainv u] (Ainv u v' Ainv)) w
	 * = Ainv w - 1/[1+v'Ainv u] (Ainv u v' Ainv) w
	 * = Ainv w - 1/[1+v'Ainv u] (Ainv u) (v' (Ainv w))
	 * = Ainv*w - 1/[1+v.Ainv.u] (Ainv*u) [v.Ainv.w]
	 * Last line in vector notation with  . inner product, * Hadamard.
	 */
	public static double[] diagSMRightMultuply(double[] w, double[] Ainv, double[] u, double[] v) {
		throw new RuntimeException("unimplemented");
	}

//	public static void main(String[] args) {
////		double[] Ainv = new double[]{ .1, .1, .1};
////		double[] u    = new double[]{ 0.7,0.2,0.1};
////		double[] v = Arr.copy(u);
//		
//		double[] Ainv = new double[]{0.4444444444444444, 0.4444444444444444, 0.4444444444444444, 0.4444444444444444};
//		double[] u = new double[]{0.25, 0.25, 0.25, 0.25};
//		double[] v = Arr.multiply(u, -1);
//
//		
//		double[][] inv = diagonalizedShermanMorrisonInverse(Ainv, u, v);
//		U.p(inv);
//	}
	
	public static int[] topKIndices(double[] arr, int K) {
		double maxVal = Arr.max(arr);
		List<Integer> bests = Lists.newArrayList();
		for (int i=0; i < arr.length; i++) {
			if (arr[i]==maxVal)
				bests.add(i);
		}
		if (bests.size() > K) {
			return Arr.sampleArrayWithoutReplacement(K, Arr.asPrimitiveIntArray(bests));
		}
		return topKIndicesViaSort(arr, K);
	}
	public static int[] topKIndicesViaSort(double[] arr, int K) {
		// would be better to use priority queue
//		MinMaxPriorityQueue<Integer> queue = MinMaxPriorityQueue.maximumSize(K).create();
		// instead do it the dumb way: sort everything
		int[] indices = Arr.rangeInts(arr.length);
		DoubleArrays.radixSortIndirect(indices, arr, true);
		// they're in ascending order. since this is top-K, get from end.
		int[] ret = Arr.sliceFromEnd(indices, K);
		return Arr.flip(ret);	
	}	
	
	///////////////////////////////////////
	
	public static double dirmultSymmLogprob(int[] countvec, double alpha_symm) {
		int N = Arr.sum(countvec);
		return dirmultSymmLogprob(countvec, N, alpha_symm);
	}
	public static double dirmultSymmLogprob(double[] countvec, double alpha_symm) {
		double N = Arr.sum(countvec);
		return dirmultSymmLogprob(countvec, N, alpha_symm);
	}
	/** Single-path version.  For a symmetric Dirichlet.
	 * DM1(x|a) = G(A)/G(A+N) \prod_k G(a_k + x_k) / G(a_k)
	 * where A=sum(a_k), N=sum(x_k)
	 * @param alpha_symm: the a_k measure param (symm so same for all k), i.e. mean times concentration **/
	public static double dirmultSymmLogprob(int[] countvec, int countsum, double alpha_symm) {
		int K = countvec.length;
		int N = countsum;
		double A = alpha_symm*K; // concentration
		//      lG(A) - lG(A+N) +          sum_k lG(a_k + n_k)            -         sum_k lG(a_k)
		return -pochhammer(A,N) + faster_lgamma_sum(countvec, alpha_symm) - K*SloppyMath.lgamma(alpha_symm);
	}
	public static double dirmultSymmLogprob(double[] countvec, double countsum, double alpha_symm) {
		int K = countvec.length;
		double N = countsum;
		double A = alpha_symm*K; // concentration
		//      lG(A) - lG(A+N) +          sum_k lG(a_k + n_k)            -         sum_k lG(a_k)
		return -pochhammer(A,N) + faster_lgamma_sum(countvec, alpha_symm) - K*SloppyMath.lgamma(alpha_symm);
	}
//	public static void main(String args[]) {
//		U.p(dirmultSymmLogprob(new int[]{14, 0, 12, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 3, 0, 18, 0, 0, 0, 0}, .1));
//		U.p(dirmultSymmLogprob(new int[]{5, 0, 13, 0, 0, 0, 0, 0, 0, 2, 0, 0, 1, 4, 0, 24, 0, 0, 0, 0}, .1));
//		U.p(dirmultSymmLogprob(new int[]{0,0,0,0,100,0}, 1));
//		U.p(dirmultSymmLogprob(new int[]{0,0,0,0,50,50}, 1));
//		U.p(dirmultSymmLogprob(new int[]{0,0,25,25,25,25}, 1));
//		U.p(dirmultSymmLogprob(new int[]{0,0,25,25,26,24}, 1));
//		U.p(dirmultSymmLogprob(new int[]{14, 0, 12, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 3, 0, 18, 0, 0, 0, 0}, 49,.1));
//		U.p(dirmultSymmLogprob(new int[]{5, 0, 13, 0, 0, 0, 0, 0, 0, 2, 0, 0, 1, 4, 0, 24, 0, 0, 0, 0}, 49,.1));
//	}
	
	/** 
	 * Quickly compute \sum_k lgamma(countvec + alphascalar)
	 * where many entries of countvec are zero -- sparse data but densely represented.
	 * theoretically we could cache other low-count values also, but hard to get a speedup
	 * same as: scipy.special.gammaln(countvec + alpha_symm).sum() 
	 **/
	public static double faster_lgamma_sum(int[] countvec, double alpha_symm) { 
	    double zeroval = SloppyMath.lgamma(alpha_symm);
	    int ii, xx;
	    int N = countvec.length;
	    double ss = 0;
	    for (ii=0; ii < N; ii++) {
	        xx = countvec[ii];
	        if (xx == 0) {
	        	ss += zeroval;
	        } else {
	            ss += SloppyMath.lgamma(xx + alpha_symm);
	        }
	    }
	    return ss;
	}
	public static double faster_lgamma_sum(double[] countvec, double alpha_symm) { 
	    double zeroval = SloppyMath.lgamma(alpha_symm);
	    int ii;
	    double xx;
	    int N = countvec.length;
	    double ss = 0;
	    for (ii=0; ii < N; ii++) {
	        xx = countvec[ii];
	        if (xx == 0) {
	        	ss += zeroval;
	        } else {
	            ss += SloppyMath.lgamma(xx + alpha_symm);
	        }
	    }
	    return ss;
	}
	static void lgammaSumTest(String[] args) {
		int N = 10000000;
		int[] nums = new int[N];
		
		for (int i=0; i<N; i++) {
			if (Math.random() < 0.3) nums[i]=0;
			else nums[i] = (int) Math.round(Math.random() * 10);
		}
		for (int itr=0; itr<100; itr++) {
			faster_lgamma_sum(nums, 3.2);
		}
	}

	
	static class PHState {
		final static int CACHE_SIZE = 200;
		static double cache_x = -1;
		static double[] cache_v = new double[CACHE_SIZE];
		static int max_cached;
	}
	
	/** adapted from Tom Minka's lightspeed library
	 * but in java it doesn't seem to be any fancer than pochhammer_slow().
	 * Requires: n >= 0 **/
	static double pochhammer_fancy(double x, int n) {
	  double result;
	  int i;
	  /* the maximum n for which we have a cached value */
	  if(n == 0) return 0;
	  if(n > PHState.CACHE_SIZE) {
	    if(x >= 1.e4*n) {
	      return Math.log(x) + (n-1)*Math.log(x+n/2);
	    }
	    return SloppyMath.lgamma(x+n) - SloppyMath.lgamma(x);
	  }
	  if(x != PHState.cache_x) {
		  PHState.max_cached = 1;
		  PHState.cache_v[0] = Math.log(x);
		  PHState.cache_x = x;
	  }
	  if(n <= PHState.max_cached) return PHState.cache_v[n-1];
	  result = PHState.cache_v[PHState.max_cached-1];
	  x = x + PHState.max_cached-1;
	  for(i=PHState.max_cached;i<n;i++) {
	    x = x + 1;
	    result += Math.log(x);
	    PHState.cache_v[i] = result;
	  }
	  PHState.max_cached = n;
	  return result;
	}

	static double pochhammer_slow(double x, int n) {
	    return SloppyMath.lgamma(x+n) - SloppyMath.lgamma(x);
	}
	static double pochhammer(double x, int n) {
	    return SloppyMath.lgamma(x+n) - SloppyMath.lgamma(x);
	}
	static double pochhammer(double x, double n) {
		return SloppyMath.lgamma(x+n) - SloppyMath.lgamma(x);
	}

	
	/* Test:
	~/ptab % ./java.sh Util > jout
	~/ptab % python ~/sem/semdoc/stats/test.py > pout
	~/ptab % diff -u pout =(perl -pe 's/-Infinity/-inf/' < jout)
	 */
	static void pochhammerTest() {
		int N = 700;
		for (int i=0;i<N;i++)
			for (int j=0;j<N;j++)
				U.pf("%d %d %.3f\n", i, j, pochhammer(i,j));
	}
//	public static void main(String args[]) {pochhammerTest();}

}

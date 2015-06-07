package util;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.*;

/**
 * An RNG class that's faster and hopefully better than java.util.Random.
 * 
 * (1) Is NOT synchronized!  Thus NOT thread-safe.  Therefore faster.
 * 	   In most applications I can think of, every thread should get its own RNG.
 * 	   Threadsafe can be nice for convenient utility functions; either wrap a FastRandom in a ThreadLocal,
 * 	   or use java.util.Random for that. 
 * (2) Uses the XORShift RNG algorithm, which is faster and more accurate (more random)
 *     than the algorithm in java.util.Random.
 * (3) Folds in the high-level methods from Mallet's cc.mallet.util.Randoms
 * (4) Leaves out some of the convenience methods that appear in java.util.Random but not Mallet.
 * 
 * XORShift implementation from http://maths.uncommons.org/
 * Mallet methods from http://mallet.cs.umass.edu/
 * ... i have no idea what license status that leaves this file
 * 
 * See discussions e.g.
 * http://stackoverflow.com/questions/453479/how-good-is-java-util-random
 * http://www.cs.gmu.edu/~sean/research/mersenne/
 * 
 * Speed tests, compared to java.util.Random - see main(): 
 *  - 10.7 times faster for nextUniform()
 *  - 1.8 times faster for nextInt(1000)
 *  - I swapped this into an LDA collapsed Gibbs sampler, and got a 5% speedup of the entire program
 * 
 * Looking for feedback / bug reports.
 * 
 * @author Brendan O'Connor (http://brenocon.com), Jan 2012, https://gist.github.com/4561065
 */
public class FastRandom implements Serializable {
	static final long serialVersionUID = -1L;
	
	private static ThreadLocal<FastRandom> _rand = new ThreadLocal<FastRandom>() {
		 @Override
	     protected FastRandom initialValue() {
			 return new FastRandom();
		 }
	};
	/** Access a threadlocal FastRandom (presumably faster than synchronized global Random) */
	public static FastRandom rand() { return _rand.get(); }

		
	/////////////  RNG section, from http://maths.uncommons.org/
	// additions are marked
	
	// ============================================================================
	//  Copyright 2006-2012 Daniel W. Dyer
	//
	//  Licensed under the Apache License, Version 2.0 (the "License");
	//  you may not use this file except in compliance with the License.
	//  You may obtain a copy of the License at
	//
	//      http://www.apache.org/licenses/LICENSE-2.0
	//
	//  Unless required by applicable law or agreed to in writing, software
	//  distributed under the License is distributed on an "AS IS" BASIS,
	//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	//  See the License for the specific language governing permissions and
	//  limitations under the License.
	//============================================================================
	// package org.uncommons.maths.random;
	// public class XORShiftRNG extends Random implements RepeatableRNG

	
    private static final int SEED_SIZE_BYTES = 20; // Needs 5 32-bit integers.

    // Previously used an array for state but using separate fields proved to be
    // faster.
    private int state1;
    private int state2;
    private int state3;
    private int state4;
    private int state5;

    private final byte[] seed;



    /**
     * Creates a new RNG and seeds it using the default seeding strategy.
     */
    public FastRandom()
    {
        this(seedFromTimeHost());
    }
    
    public FastRandom(long seed) {
    	this(seedFromString("happyinteger" + seed));
    }
    
    static private volatile int uniquifier = 0;  // so multiple RNG's created in the same process are forced unique

    /** highly lame @author brendano */
    static byte[] seedFromTimeHost() {
    	long t = System.nanoTime();
    	String s = t + " " + (++uniquifier) + " " + ManagementFactory.getRuntimeMXBean().getName();
    	return seedFromString(s);
    }
    
    static byte[] seedFromString(String s) {
        ByteBuffer bb = ByteBuffer.allocate(20);
    	bb.putInt((s+"1").hashCode());
    	bb.putInt((s+"2").hashCode());
    	bb.putInt((s+"3").hashCode());
    	bb.putInt((s+"4").hashCode());
    	bb.putInt((s+"5").hashCode());
    	return bb.array();
    }

    /**
     * Creates an RNG and seeds it with the specified seed data.
     * @param seed The seed data used to initialise the RNG.
     */
    public FastRandom(byte[] seed)
    {
        if (seed == null || seed.length != SEED_SIZE_BYTES)
        {
            throw new IllegalArgumentException("XOR shift RNG requires 160 bits of seed data.");
        }
        this.seed = seed.clone();
        int[] state = convertBytesToInts(seed);
        this.state1 = state[0];
        this.state2 = state[1];
        this.state3 = state[2];
        this.state4 = state[3];
        this.state5 = state[4];
    }


    /**
     * {@inheritDoc}
     */
    public byte[] getSeed()
    {
        return seed.clone();
    }


    protected int next(int bits)
    {
        int t = (state1 ^ (state1 >> 7));
        state1 = state2;
        state2 = state3;
        state3 = state4;
        state4 = state5;
        state5 = (state5 ^ (state5 << 6)) ^ (t ^ (t << 13));
        int value = (state2 + state2 + 1) * state5;
        return value >>> (32 - bits);
    }

    /// from BinaryUtils
    
    // Mask for casting a byte to an int, bit-by-bit (with
    // bitwise AND) with no special consideration for the sign bit.
    private static final int BITWISE_BYTE_TO_INT = 0x000000FF;
    
    /**
     * Take four bytes from the specified position in the specified
     * block and convert them into a 32-bit int, using the big-endian
     * convention.
     * @param bytes The data to read from.
     * @param offset The position to start reading the 4-byte int from.
     * @return The 32-bit integer represented by the four bytes.
     */
    public static int convertBytesToInt(byte[] bytes, int offset)
    {
        return (BITWISE_BYTE_TO_INT & bytes[offset + 3])
                | ((BITWISE_BYTE_TO_INT & bytes[offset + 2]) << 8)
                | ((BITWISE_BYTE_TO_INT & bytes[offset + 1]) << 16)
                | ((BITWISE_BYTE_TO_INT & bytes[offset]) << 24);
    }
    
    /**
     * Convert an array of bytes into an array of ints.  4 bytes from the
     * input data map to a single int in the output data.
     * @param bytes The data to read from.
     * @return An array of 32-bit integers constructed from the data.
     * @since 1.1
     */
    public static int[] convertBytesToInts(byte[] bytes)
    {
        if (bytes.length % 4 != 0)
        {
            throw new IllegalArgumentException("Number of input bytes must be a multiple of 4.");
        }
        int[] ints = new int[bytes.length / 4];
        for (int i = 0; i < ints.length; i++)
        {
            ints[i] = convertBytesToInt(bytes, i * 4);
        }
        return ints;
    }
    
    ////////////////// END 	/////////////  RNG section, from http://maths.uncommons.org/
    
    /////////////////  START java.util.Random section
    
    public int nextInt(int n) {
        if (n <= 0)
            throw new IllegalArgumentException("n must be positive");

        if ((n & -n) == n)  // i.e., n is a power of 2
            return (int)((n * (long)next(31)) >> 31);

        int bits, val;
        do {
            bits = next(31);
            val = bits % n;
        } while (bits - val + (n-1) < 0);
        return val;
    }
    
    /////////////////  END java.util.Random section
    
    /////////////////  START cc.mallet.util.Randoms section
    
    /* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
    This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
    http://www.cs.umass.edu/~mccallum/mallet
    This software is provided under the terms of the Common Public License,
    version 1.0, as published by http://www.opensource.org.  For further
    information, see the file `LICENSE' included with this distribution. */



    /** Return random integer from Poission with parameter lambda.  
     * The mean of this distribution is lambda.  The variance is lambda. */
    public int nextPoisson(double lambda) {
    	int i,j,v=-1;
    	double l=Math.exp(-lambda),p;
    	p=1.0;
    	while (p>=l) {
    		p*=nextUniform();
    		v++;
    	}
    	return v;
    }

    /** Return nextPoisson(1). */
    public int nextPoisson() {
    	return nextPoisson(1);
    }

    /** Return a random boolean, equally likely to be true or false. */
    public boolean nextBoolean() {
    	return (next(32) & 1 << 15) != 0;
    }

    /** Return a random boolean, with probability p of being true. */
    public boolean nextBoolean(double p) {
    	double u=nextUniform();
    	if(u < p) return true;
    	return false;
    }

    /** Return a random BitSet with "size" bits, each having probability p of being true. */
    public BitSet nextBitSet (int size, double p)
    {
    	BitSet bs = new BitSet (size);
    	for (int i = 0; i < size; i++)
    		if (nextBoolean (p)) {
    			bs.set (i);
    		}
    	return bs;
    }

    /** Return a random double in the range 0 to 1, inclusive, uniformly sampled from that range. 
     * The mean of this distribution is 0.5.  The variance is 1/12. */
    public double nextUniform() {
    	long l = ((long)(next(26)) << 27) + next(27);
    	return l / (double)(1L << 53);
    }

    /** Return a random double in the range a to b, inclusive, uniformly sampled from that range.
     * The mean of this distribution is (b-a)/2.  The variance is (b-a)^2/12 */
    public double nextUniform(double a,double b) {
    	return a + (b-a)*nextUniform();
    }

    /** Draw a single sample from multinomial "a". ASSUME SUMS to 1 ! */
    public int nextDiscrete (double[] a) {
    	double b = 0, r = nextUniform();
    	for (int i = 0; i < a.length; i++) {
    		b += a[i];
    		if (b > r) {
    			return i;
    		}
    	}
    	return a.length-1;
    }

    /** draw a single sample from (unnormalized) multinomial "a", with normalizing factor "sum". */
    public int nextDiscrete (double[] a, double sum) {
    	double b = 0, r = nextUniform() * sum;
    	for (int i = 0; i < a.length; i++) {
    		b += a[i];
    		if (b > r) {
    			return i;
    		}
    	}
    	return a.length-1;
    }

    private double nextGaussian;
    private boolean haveNextGaussian = false;

    /** Return a random double drawn from a Gaussian distribution with mean 0 and variance 1. */
    public double nextGaussian() {
    	if (!haveNextGaussian) {
    		double v1=nextUniform(),v2=nextUniform();
    		double x1,x2;
    		x1=Math.sqrt(-2*Math.log(v1))*Math.cos(2*Math.PI*v2);
    		x2=Math.sqrt(-2*Math.log(v1))*Math.sin(2*Math.PI*v2);
    		nextGaussian=x2;
    		haveNextGaussian=true;
    		return x1;
    	}
    	else {
    		haveNextGaussian=false;
    		return nextGaussian;
    	}
    }

    /** Return a random double drawn from a Gaussian distribution with mean m and variance s2. */
    public double nextGaussian(double mean, double var) {
    	assert var > 0;
    	return nextGaussian()*Math.sqrt(var) + mean;
    }

    // generate Gamma(1,1)
    // E(X)=1 ; Var(X)=1
    /** Return a random double drawn from a Gamma distribution with mean 1.0 and variance 1.0. */
    public double nextGamma() {
    	return nextGamma(1,1,0);
    }

    /** Return a random double drawn from a Gamma distribution with mean alpha and variance 1.0. */
    public double nextGamma(double alpha) {
    	return nextGamma(alpha,1,0);
    }

    /* Return a sample from the Gamma distribution, with parameter IA */
    /* From Numerical "Recipes in C", page 292 */
    public double oldNextGamma (int ia)
    {
    	int j;
    	double am, e, s, v1, v2, x, y;

    	assert (ia >= 1) ;
    	if (ia < 6) 
    	{
    		x = 1.0;
    		for (j = 1; j <= ia; j++)
    			x *= nextUniform ();
    		x = - Math.log (x);
    	}
    	else
    	{
    		do
    		{
    			do
    			{
    				do
    				{
    					v1 = 2.0 * nextUniform () - 1.0;
    					v2 = 2.0 * nextUniform () - 1.0;
    				}
    				while (v1 * v1 + v2 * v2 > 1.0);
    				y = v2 / v1;
    				am = ia - 1;
    				s = Math.sqrt (2.0 * am + 1.0);
    				x = s * y + am;
    			}
    			while (x <= 0.0);
    			e = (1.0 + y * y) * Math.exp (am * Math.log (x/am) - s * y);
    		}
    		while (nextUniform () > e);
    	}
    	return x;
    }


    /** Return a random double drawn from a Gamma distribution with mean alpha*beta and variance alpha*beta^2. */
    public double nextGamma(double alpha, double beta) {
    	return nextGamma(alpha,beta,0);
    }

    /** Return a random double drawn from a Gamma distribution
     *  with mean alpha*beta+lamba and variance alpha*beta^2.
     *  Note that this means the pdf is: 
     *     <code>frac{ x^{alpha-1} exp(-x/beta) }{ beta^alpha Gamma(alpha) }</code>
     *  in other words, beta is a "scale" parameter. An alternative 
     *  parameterization would use 1/beta, the "rate" parameter.
     */
    public double nextGamma(double alpha, double beta, double lambda) {
    	double gamma=0;
    	if (alpha <= 0 || beta <= 0) {
    		throw new IllegalArgumentException ("alpha and beta must be strictly positive.");
    	}
    	if (alpha < 1) {
    		double b,p;
    		boolean flag = false;

    		b = 1 + alpha * Math.exp(-1);

    		while (!flag) {
    			p = b * nextUniform();
    			if (p > 1) {
    				gamma = -Math.log((b - p) / alpha);
    				if (nextUniform() <= Math.pow(gamma, alpha - 1)) {
    					flag = true;
    				}
    			}
    			else {
    				gamma = Math.pow(p, 1.0/alpha);
    				if (nextUniform() <= Math.exp(-gamma)) {
    					flag = true;
    				}
    			}
    		}
    	}
    	else if (alpha == 1) {
    		// Gamma(1) is equivalent to Exponential(1). We can 
    		//  sample from an exponential by inverting the CDF:

    		gamma = -Math.log (nextUniform ());

    		// There is no known closed form for Gamma(alpha != 1)...
    	}
    	else {

    		// This is Best's algorithm: see pg 410 of
    		//  Luc Devroye's "non-uniform random variate generation"
    		// This algorithm is constant time for alpha > 1.

    		double b = alpha - 1;
    		double c = 3 * alpha - 0.75;

    		double u, v;
    		double w, y, z;

    		boolean accept = false;

    		while (! accept) {
    			u = nextUniform();
    			v = nextUniform();

    			w = u * (1 - u);
    			y = Math.sqrt( c / w ) * (u - 0.5);
    			gamma = b + y;

    			if (gamma >= 0.0) {
    				z = 64 * w * w * w * v * v;  // ie: 64 * w^3 v^2

    				accept = z <= 1.0 - ((2 * y * y) / gamma);

    				if (! accept) {
    					accept = (Math.log(z) <=
    						2 * (b * Math.log(gamma / b) - y));
    				}
    			}
    		}

    		/* // Old version, uses time linear in alpha
			   double y = -Math.log (nextUniform ());
			   while (nextUniform () > Math.pow (y * Math.exp (1 - y), alpha - 1))
			   y = -Math.log (nextUniform ());
			   gamma = alpha * y;
    		 */
    	}
    	return beta*gamma+lambda;
    }

    /** Return a random double drawn from an Exponential distribution with mean 1 and variance 1. */
    public double nextExp() {
    	return nextGamma(1,1,0);
    }

    /** Return a random double drawn from an Exponential distribution with mean beta and variance beta^2. */
    public double nextExp(double beta) {
    	return nextGamma(1,beta,0);
    }

    /** Return a random double drawn from an Exponential distribution with mean beta+lambda and variance beta^2. */
    public double nextExp(double beta,double lambda) {
    	return nextGamma(1,beta,lambda);
    }

    /** Return a random double drawn from an Chi-squarted distribution with mean 1 and variance 2. 
     * Equivalent to nextChiSq(1) */
    public double nextChiSq() {
    	return nextGamma(0.5,2,0);
    }

    /** Return a random double drawn from an Chi-squared distribution with mean df and variance 2*df.  */
    public double nextChiSq(int df) {
    	return nextGamma(0.5*(double)df,2,0);
    }

    /** Return a random double drawn from an Chi-squared distribution with mean df+lambda and variance 2*df.  */
    public double nextChiSq(int df,double lambda) {
    	return nextGamma(0.5*(double)df,2,lambda);
    }

    /** Return a random double drawn from a Beta distribution with mean a/(a+b) and variance ab/((a+b+1)(a+b)^2).  */
    public double nextBeta(double alpha,double beta) {
    	if (alpha <= 0 || beta <= 0) {
    		throw new IllegalArgumentException ("alpha and beta must be strictly positive.");
    	}
    	if (alpha == 1 && beta == 1) {
    		return nextUniform ();
    	} else if (alpha >= 1 && beta >= 1) {
    		double A = alpha - 1,
    		B = beta - 1,
    		C = A + B,
    		L = C * Math.log (C),
    		mu = A / C,
    		sigma = 0.5 / Math.sqrt (C);
    		double y = nextGaussian (), x = sigma * y + mu;
    		while (x < 0 || x > 1) {
    			y = nextGaussian ();
    			x = sigma * y + mu;
    		}
    		double u = nextUniform ();
    		while (Math.log (u) >= A * Math.log (x / A) + B * Math.log ((1 - x) / B) + L + 0.5 * y * y) {
    			y = nextGaussian ();
    			x = sigma * y + mu;
    			while (x < 0 || x > 1) {
    				y = nextGaussian ();
    				x = sigma * y + mu;
    			}
    			u = nextUniform ();
    		}
    		return x;
    	} else {
    		double v1 = Math.pow (nextUniform (), 1 / alpha),
    		v2 = Math.pow (nextUniform (), 1 / beta);
    		while (v1 + v2 > 1) {
    			v1 = Math.pow (nextUniform (), 1 / alpha);
    			v2 = Math.pow (nextUniform (), 1 / beta);
    		}
    		return v1 / (v1 + v2);
    	}
    }


  //////////////////////////  END mallet section
    
//    static double qcauchy(double p, double center, double gamma) {
//    	// http://en.wikipedia.org/wiki/Cauchy_distribution#Cumulative_distribution_function
//    	return center + gamma * Math.tan(Math.PI * (p - 0.5));
//    }

    //////////////////////
    
    public static void main(String[] args) {
    	FastRandom r = new FastRandom();
//    	java.util.Random r = new java.util.Random();
    	// Mallet "Randoms" uses java.util.Random's RNG (it's a subclass), and has nextUniform()
//    	cc.mallet.util.Randoms r = new cc.mallet.util.Randoms();
    	
    	int niter = (int) 5e8;
    	int lim = 32;
		double t0 = (double) System.nanoTime();
		for (int i=0; i < niter; i++) {
//			int y = r.nextInt(lim);
			double y = r.nextUniform();
//			System.out.println(y);
		}
		double elapsed = System.nanoTime() - t0;
		System.out.printf("%g s total, %g ns/iter\n", elapsed/1e9, elapsed/niter);

    }
}

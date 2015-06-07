import com.google.common.base.Function;
import com.google.common.collect.*;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.util.*;

import util.Arr;
import util.BasicFileIO;
import util.FastRandom;
import util.MCMC;
import util.U;
import util.Util;
import util.Vocabulary;

/**
 * collapsed Gibbs sampling for LDA
 * with slice resampling of both concentrations
 * 
 * uses a token-level denormalized format:
 * the dataset is viewed as a giant vector of tuples
 *   (d,w)
 * this is annoying/wasteful for document BOW modeling, but nice for extensions into more structured contexts.
 * 
 * Input file format is two-column TSV
 *   DocID \t Word
 * one line per token in the corpus.
 * DocID and Word can both be strings.
 * 
 * Run:
 *   ./java.sh LDA 10 data/nber.sample out | tee log
 *
 * Get at traceplot info:  (console plot for a good time https://gist.github.com/brendano/5114194)
 *   grep totalLL log
 *   grep docConc log
 *   grep wordConc log
 * 
 * View the topics:  (after say 500 iterations)
 *   python scripts/view.py out/model.500 > topics.html
 * 
 */
public class LDA {

	double docConc = 0.1;   // "alpha"
	double wordConc = 100;  // "beta"
	
	static class Opts {
		static String dataFilename = "data/docwords.txt";
		static String outputDir = "out";
		static int maxIter = 100000 + 1;
		static int saveEvery = 1000;
		static int concResampleEvery = 100;
	}
	
	int numTopics = 5;
	int numWordTypes = -1;

	// docid and wordtype vocabularies
	Vocabulary wordVocab;
	Vocabulary docVocab;
	
	ArrayList<Tuple> dataTuples;
	
	// CGS token count tables
	double[][] nDocTopic;
	double[][] nWordTopic;
	double[] nTopic;
	double[] nDoc;  // static during sampling
	double[] nWord; // static during sampling
	
	// the z's
	int[] tokenClasses;
	
	static class Tuple {
		int docid;
		int wordid;
	}
	
	FastRandom rand = new FastRandom();
	
	public LDA() {
		wordVocab = new Vocabulary();
		docVocab = new Vocabulary();
		dataTuples = Lists.newArrayList();
	}
	
	void readData() {
		U.pf("reading %s\n", Opts.dataFilename);
		for (String line : BasicFileIO.openFileLines(Opts.dataFilename)) {
			String[] parts = line.split("\t");
			String docid = parts[0];
			String word = parts[1];
			
			Tuple t = new Tuple();
			t.docid = docVocab.num(docid);
			t.wordid= wordVocab.num(word);
			dataTuples.add(t);
		}
		numWordTypes = wordVocab.size();
		U.pf("%d docs, %d wordtypes, %d tokens\n", docVocab.size(), wordVocab.size(), dataTuples.size());
	}

	void saveInitial() {
		try {
			docVocab.dump(U.sf("%s/doc.vocab", Opts.outputDir));
			wordVocab.dump(U.sf("%s/word.vocab", Opts.outputDir));
		} catch (IOException e) {
			// TODO Auto-generated catch block
		}
	}
	
	void setupModel() {
		int numDocs = docVocab.size();
		int numWords= wordVocab.size();
		nDocTopic = new double[numDocs][numTopics];
		nWordTopic= new double[numWords][numTopics];
		nTopic = new double[numTopics];
		nDoc = new double[numDocs];
		nWord= new double[numWords];
		tokenClasses = new int[dataTuples.size()];
		doDataCounts();
	}
	void doDataCounts() {
		for (Tuple t : dataTuples) {
			nDoc[t.docid]++;
			nWord[t.wordid]++;
		}
	}
	
	/** delta=+1 for increment.  delta=-1 for decrement. */
	void updateCounts(Tuple tuple, int k, int delta) {
		nDocTopic[tuple.docid][k] += delta;
		nWordTopic[tuple.wordid][k] += delta;
		nTopic[k] += delta;
	}
	
	/** output the sampling field into 'field', and return its sum */
	double calcTopicField(Tuple tuple, double[] field) {
		double psum=0;
		for (int k=0; k < numTopics; k++) {
			// P(f |doc,word)
			// \propto P(f | doc) P(word | doc) = (#TopicDoc/#Doc) (#WordTopic/#Topic)
			// where #Doc is irrelevant to the sampling field
			double docFactor =  nDocTopic[tuple.docid][k] + docConc/numTopics;
			double wordFactor  = 
				(nWordTopic[tuple.wordid][k] + wordConc/numWordTypes) / (nTopic[k] + wordConc);
			double w = docFactor * wordFactor;
			field[k] = w;
			psum += w;
		}
		return psum;
	}

	void sampleIteration(boolean firstIteration) {
		double unnormField[] = new double[numTopics];
		for (int i=0; i < dataTuples.size(); i++) {
			Tuple tuple = dataTuples.get(i);

			if (!firstIteration) {
				updateCounts(tuple, tokenClasses[i], -1);	
			}			
			double psum = calcTopicField(tuple, unnormField);
			int newK = rand.nextDiscrete(unnormField, psum);
			updateCounts(tuple, newK, +1);
			tokenClasses[i] = newK;
		}
	}
	
	/** this does NOT use model's concentration; instead the passed-in value. **/
	double calcClassLL(double _docConc) {
		double ll = 0;
		for (int docid=0; docid < docVocab.size(); docid++) {
			ll += Util.dirmultSymmLogprob(nDocTopic[docid], nDoc[docid], _docConc/numTopics);
		}
		return ll;
	}
	/** this does NOT use model's concentration; instead the passed-in value. **/
	double calcWordLL(double _wordConc) {
		double ll = 0;
		for (int k=0; k < numTopics; k++) {
			double vec[] = Arr.getCol(nWordTopic, k);
			ll += Util.dirmultSymmLogprob(vec, nTopic[k], _wordConc/numWordTypes);
		}
		return ll;
	}

	double calcWordLL() {
		return calcWordLL(wordConc);
	}
	double calcClassLL() {
		return calcClassLL(docConc);
	}

	void resampleConcs() {
		Function <double[],Double> zLL = new Function<double[],Double>() {
			@Override
			public Double apply(double[] input) {
				return calcClassLL(Math.exp(input[0]));
			}
		};
		Function <double[],Double> wLL = new Function<double[],Double>() {
			@Override
			public Double apply(double[] input) {
				return calcWordLL(Math.exp(input[0]));
			}
		};
		List<double[]> history;

		history = MCMC.slice_sample(wLL, new double[]{Math.log(wordConc)}, new double[]{1}, 30);
		this.wordConc  = Math.exp(history.get(history.size()-1)[0]);
		U.pf("wordConc %.6g\n", wordConc);
		
		history = MCMC.slice_sample(zLL, new double[]{Math.log(docConc)}, new double[]{1}, 30);
		this.docConc  = Math.exp(history.get(history.size()-1)[0]);
		U.pf("docConc %.6g\n", docConc);
	}
	
	void saveModel(int iter) {
		String prefix = U.sf("%s/model.%d", Opts.outputDir, iter);
		Arr.write(nDocTopic, U.sf("%s.nDocTopic", prefix));
		Arr.write(nWordTopic, U.sf("%s.nWordTopic", prefix));
		Arr.write(nTopic, U.sf("%s.nTopic", prefix));
	}
	
	void train() {
		sampleIteration(true);

		for (int iter=1; iter<Opts.maxIter; iter++) {
			U.pf("ITER %d\n", iter);
			sampleIteration(false);
			
			if (iter<=100 || iter % 20 == 0) {
				double wordLL = calcWordLL();
				double classLL= calcClassLL();
				U.pf("totalLL %f\n", wordLL+classLL);
			}
			
			if (iter % Opts.concResampleEvery == 0 && Opts.concResampleEvery>=0) {
				resampleConcs();
			}
			if (iter % Opts.saveEvery == 0) {
				U.p("saving");
				saveModel(iter);
			}
		}
	}
	
	public static void main(String[] args) throws Exception {
		LDA m = new LDA();
		
		m.numTopics = Integer.valueOf(args[0]);
		LDA.Opts.dataFilename = args[1];
		LDA.Opts.outputDir = args[2];
		
		Files.createParentDirs(new File(LDA.Opts.outputDir + "/bla"));		
		m.readData();
		m.setupModel();
		m.saveInitial();
		m.train();
	}
}

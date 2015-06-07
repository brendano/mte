package vocabalts;

import gnu.trove.TObjectIntHashMap;
import util.BasicFileIO;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
//import gnu.trove.map.hash.TObjectIntHashMap;


/**
 * Feature vocabulary.
 * Implemented with Trove String=>int map.
 * 
 * Empirical comparison.
 * Task: load ~3million feature vocab (English data).  5 trials per setting.
 
java 1.8
 
== Java String keys with HashMap ==
  init = 134217728(131072K) used = 501040456(489297K) committed = 547880960(535040K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithString src/vocabalts/featnames.txt  20.42s user 0.47s system 179% cpu 11.643 total
  init = 134217728(131072K) used = 501011664(489269K) committed = 612892672(598528K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithString src/vocabalts/featnames.txt  23.45s user 0.49s system 180% cpu 13.259 total
  init = 134217728(131072K) used = 501040240(489297K) committed = 548929536(536064K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithString src/vocabalts/featnames.txt  20.36s user 0.43s system 178% cpu 11.656 total

== Java String keys, TObjectIntHashMap, Trove 3.0.3 ==
  init = 134217728(131072K) used = 369319792(360663K) committed = 433586176(423424K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithString src/vocabalts/featnames.txt  10.55s user 0.38s system 165% cpu 6.610 total
  init = 134217728(131072K) used = 368895040(360249K) committed = 401080320(391680K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithString src/vocabalts/featnames.txt  12.23s user 0.37s system 166% cpu 7.580 total
  init = 134217728(131072K) used = 369396088(360738K) committed = 463994880(453120K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithString src/vocabalts/featnames.txt  10.78s user 0.38s system 163% cpu 6.832 total

== Java String keys, Object2IntOpenHashMap, fastutil 6.4.6 ==
  init = 134217728(131072K) used = 392080048(382890K) committed = 503840768(492032K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithString src/vocabalts/featnames.txt  9.97s user 0.36s system 161% cpu 6.400 total
  init = 134217728(131072K) used = 391759608(382577K) committed = 519045120(506880K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithString src/vocabalts/featnames.txt  11.24s user 0.36s system 163% cpu 7.077 total
  init = 134217728(131072K) used = 391973248(382786K) committed = 505413632(493568K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithString src/vocabalts/featnames.txt  10.67s user 0.36s system 163% cpu 6.756 total

 */
public class VocabWithString {

	private TObjectIntHashMap<String> name2num= new TObjectIntHashMap<String>();
//	private Object2IntMap<String> name2num = new Object2IntOpenHashMap<String>();
//	private HashMap<String,Integer> name2num = new HashMap<String,Integer>();
	private ArrayList<String> num2name;

	private boolean isLocked = false;

	public VocabWithString() { 
		num2name = new ArrayList<>();
	}

	public void lock() {
		isLocked = true;
	}
	public boolean isLocked() { return isLocked; }

	public int size() {
		assert name2num.size() == num2name.size();
		return name2num.size();
	}
	
	/** 
	 *  If not locked, an unknown name is added to the vocabulary.
	 *  If locked, return -1 on OOV.
	 * @param featname
	 * @return
	 */
	public int num(String featname) {
//		ByteString featname = new ByteString(encodeUTF8(_featname));
		if (! name2num.containsKey(featname)) {
			if (isLocked) return -1;
			int n = name2num.size();
			name2num.put(featname, n);
			num2name.add(featname);
			return n;
		} else {
			return name2num.get(featname);
		}
	}

	public String name(int num) {
		if (num2name.size() <= num) {
			throw new RuntimeException("Unknown number for vocab: " + num);
		} else {
			return num2name.get(num);
//			return decodeUTF8(num2name.get(num).data);
		}
	}

	public boolean contains(String name) {
		return name2num.containsKey(name);
	}

//	public String toString() {
//		return "[" + StringUtils.join(num2name) + "]";
//	}

	/** Throw an error if OOV **/
	public int numStrict(String string) {
		assert isLocked;
		int n = num(string);
		if (n == -1) throw new RuntimeException("OOV happened");
		return n;
	}
	
	/** please don't modify this return value, will cause problems */
	public List<String> names() {
		return num2name;
	}
	
	public void dump(String filename) throws IOException {
		BasicFileIO.writeLines(names(), filename);
	}
	
	public static void main(String[] args) throws IOException {
//		ManagementFactory.getMemoryMXBean().setVerbose(true);
		
		VocabWithString vocab = new VocabWithString();
		for (String line : BasicFileIO.openFileLines(args[0])) {
			vocab.num(line);
		}
		System.gc();
		System.out.println(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage());
//		System.out.print("> ");
//		System.in.read();
	}

}


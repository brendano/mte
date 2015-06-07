package vocabalts;

import gnu.trove.TObjectIntHashMap;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import util.BasicFileIO;

import static vocabalts.VocabWithByteString.encodeUTF8;


/**

Results: 47% memory reduction for Trove bytestring, versus naive HashMap java String. 

Implementation strategy: use UTF-8 encoded byte arrays as keys.
So for an ASCII-heavy dataset, the string payload data would be twice as small as using String keys (since Java uses a 2-byte encoding internally).
This comes out to a 30% reduction in size: compare results against VocabWithString.

Speed/memory results, java 1.8.  
'used' memory is what matters.  
Trove looks best, though fastutil is only barely worse.

== ByteString, HashMap ==
  init = 134217728(131072K) used = 397088920(387782K) committed = 544735232(531968K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithByteString src/vocabalts/featnames.txt  20.29s user 0.44s system 177% cpu 11.667 total
  init = 134217728(131072K) used = 397107888(387800K) committed = 541065216(528384K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithByteString src/vocabalts/featnames.txt  20.59s user 0.41s system 179% cpu 11.709 total
  init = 134217728(131072K) used = 397157280(387848K) committed = 542113792(529408K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithByteString src/vocabalts/featnames.txt  21.41s user 0.44s system 174% cpu 12.500 total

== ByteString, TObjectIntHashMap (Trove 3.0.3) ==
  init = 134217728(131072K) used = 265834400(259603K) committed = 495452160(483840K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithByteString src/vocabalts/featnames.txt  10.54s user 0.36s system 153% cpu 7.111 total
  init = 134217728(131072K) used = 265599992(259374K) committed = 470810624(459776K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithByteString src/vocabalts/featnames.txt  9.48s user 0.35s system 155% cpu 6.308 total
  init = 134217728(131072K) used = 265645224(259419K) committed = 440926208(430592K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithByteString src/vocabalts/featnames.txt  10.21s user 0.35s system 152% cpu 6.942 total

== ByteString, Object2IntOpenHashMap (fastutil 6.4.6) ==
  init = 134217728(131072K) used = 288066192(281314K) committed = 468713472(457728K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithByteString src/vocabalts/featnames.txt  10.15s user 0.41s system 149% cpu 7.061 total
  init = 134217728(131072K) used = 288304744(281547K) committed = 488112128(476672K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithByteString src/vocabalts/featnames.txt  9.82s user 0.40s system 149% cpu 6.828 total
  init = 134217728(131072K) used = 288011832(281261K) committed = 479723520(468480K) max = 3817865216(3728384K)
  ./java.sh vocabalts.VocabWithByteString src/vocabalts/featnames.txt  9.88s user 0.36s system 149% cpu 6.843 total

 */
public class VocabWithByteString {

	private TObjectIntHashMap<ByteString> name2num = new TObjectIntHashMap<>();
//	private Object2IntMap<ByteString> name2num = new Object2IntOpenHashMap<>();
//	private HashMap<ByteString, Integer> name2num = new HashMap<>();
	
	private ArrayList<ByteString> num2name;

	private boolean isLocked = false;

	public VocabWithByteString() { 
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
	 * @param _featname
	 * @return
	 */
	public int num(String _featname) {
		ByteString featname = new ByteString(encodeUTF8(_featname));
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
			return decodeUTF8(num2name.get(num).data);
		}
	}

	public boolean contains(String name) {
		return name2num.containsKey(new ByteString(name));
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
	
	public List<String> names() {
		ArrayList<String> ret = new ArrayList<>();
		for (ByteString s : num2name)  ret.add(decodeUTF8(s.data));
		return ret;
	}
	
	public void dump(String filename) throws IOException {
		BasicFileIO.writeLines(names(), filename);
	}
	
	// http://stackoverflow.com/a/3386646/86684

	private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

	private static String decodeUTF8(byte[] bytes) {
	    return new String(bytes, UTF8_CHARSET);
	}

	static byte[] encodeUTF8(String string) {
	    return string.getBytes(UTF8_CHARSET);
	}


	public static void main(String[] args) throws IOException {
		VocabWithByteString vocab = new VocabWithByteString();
		for (String line : BasicFileIO.openFileLines(args[0])) {
			vocab.num(line);
		}
		System.gc();
		System.out.println(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage());

	}

}


class ByteString {
	final byte[] data;

	public ByteString(String data) {
		this(encodeUTF8(data));
	}

	public ByteString(byte[] data){
		if (data == null){
			throw new NullPointerException();
		}
		this.data = data;
	}

	@Override
	public boolean equals(Object other){
		if (!(other instanceof ByteString)){
			return false;
		}
		return Arrays.equals(data, ((ByteString) other).data);
	}

	@Override
	public int hashCode(){
		return Arrays.hashCode(data);
	}
}

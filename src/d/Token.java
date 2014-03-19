package d;

public class Token {
	public String text;
	
	// these are only sometimes filled
	public String pos;
	public String ner;

	/** inclusive */
	public int startChar = -1;
	/** exclusive */
	public int endChar = -1;
	
	public String toString() {
		if (text==null) return "[NULLTOKEN]";
		return text + "_[" + startChar + ":" + endChar + "]";
	}
}

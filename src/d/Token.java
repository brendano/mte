package d;

public class Token {
	String text;

	/** inclusive */
	int startChar = -1;
	/** exclusive */
	int endChar = -1;
	
	public String toString() {
		if (text==null) return "[NULLTOKEN]";
		return text + "_[" + startChar + ":" + endChar + "]";
	}
}

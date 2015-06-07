package te.data;
import java.util.*;
import util.U;

/** this class is underspecified what the span is about (characters? tokens? sentences?)
 * but in all cases it's intended to represent a [start,end) inc-exc pair with length (end-start).
 */
public class Span {
	public int start;
	public int end;
	public Span(int s,int e) {
		assert s <= e : String.format("illegal span %s to %s", s,e);
		start=s; end=e; }
	
	@Override public String toString() { return String.format("Span[%d,%d)", start,end); }
	@Override public boolean equals(Object o) {
		if (!(o instanceof Span)) return false;
		if (o==this) return true;
		return this.start==((Span)o).start && this.end==((Span) o).end;
	}
	@Override public int hashCode() {
		return toString().hashCode();  // todo could make faster by hashing the ints together like in Pair
	}
	public boolean contains(int i) {
		return start <= i && i < end;
	}
}

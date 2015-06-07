package te.data;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.util.StringUtils;

public class TermInstance {
	public String termName;
	public List<Integer> tokIndsInDoc;

	public TermInstance(String termName, List<Integer> inds) {
		this.termName = termName;
		this.tokIndsInDoc = inds;
	}
	
	@Override public String toString() {
		return String.format("TI[%s || tokinds %s]", termName, StringUtils.join(tokIndsInDoc));
	}
	
	/** hashcodes are unique only within a doc. */
	@Override public int hashCode() {
		int toksetHash = tokIndsInDoc.hashCode();
		return 31*toksetHash + termName.hashCode();
	}
}

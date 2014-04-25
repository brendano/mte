package d;
import java.util.*;

/** yes, it's silly to call it "inverted" but it does make it clear what it is */
public class InvertedIndex {
	private HashMap<String,List<Document>> docsByTerm;
	public InvertedIndex() {
		docsByTerm = new HashMap<>();
	}
	public void add(Document d) {
		for (String term : d.termVec.support()) {
			if (!docsByTerm.containsKey(term)) {
				docsByTerm.put(term, new ArrayList<Document>());
			}
			docsByTerm.get(term).add(d);
		}
	}
	public List<Document> getMatchingDocs(String term) {
		if ( ! docsByTerm.containsKey(term)) {
			return new ArrayList<>();
		}
		return docsByTerm.get(term);
	}
}

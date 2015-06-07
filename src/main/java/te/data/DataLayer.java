package te.data;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * @author jfoley.
 */
public interface DataLayer {
	Collection<Document> allDocs();

	Document getDocByDocnum(int docnum);

	double termSumSq(String term);

	DocSet getDocSet(Collection<String> docids);

	DocSet select(String xAttr, String yAttr, double minX, double maxX, double minY, double maxY);

	void runTokenizer(Function<String, List<Token>> tokenizer);

	DocSet select(List<String> terms);
}

package d;

import java.util.ArrayList;
import java.util.List;

public class TermInstance {
	public String termName;
	public List<Integer> tokIndsInDoc;

	public TermInstance(String termName, List<Integer> inds) {
		this.termName = termName;
		this.tokIndsInDoc = inds;
	}
}

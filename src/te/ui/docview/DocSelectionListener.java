package te.ui.docview;
import java.util.*;

import util.U;

public interface DocSelectionListener {
	public void receiveDocSelection(Collection<String> docids);
}

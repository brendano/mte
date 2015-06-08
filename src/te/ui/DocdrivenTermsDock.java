package te.ui;

import bibliothek.gui.dock.DefaultDockable;
import com.google.common.eventbus.Subscribe;
import te.ui.queries.DocSelectionChange;

/**
 * @author jfoley.
 */
class DocdrivenTermsDock extends DefaultDockable {
  private Main main;

  public DocdrivenTermsDock(Main main) {
    this.main = main;
  }

  @Subscribe
  public void updateFromDocSelection(DocSelectionChange e) {
    int n = main.AQ().docPanelSelectedDocIDs.size();
    if (n == 0) {
			setTitleText("Terms associated with document selection (empty)");
    } else {
      setTitleText(String.format("Terms associated with %d documents", n));
    }
  }
}

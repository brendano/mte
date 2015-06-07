package te.ui;

import javax.swing.*;
import java.awt.*;

/**
 * @author jfoley.
 */
class InfoArea extends JLabel {
  public InfoArea(String s) {
    super(s);
    // WTF
//			setMaximumSize(new Dimension(100,50));
    setMinimumSize(new Dimension(200, 16));
    setBackground(Color.WHITE);
  }
//		public Dimension getMaximumSize() {
////			return new Dimension(300,50);
//		}
}

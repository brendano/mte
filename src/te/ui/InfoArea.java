package te.ui;

import javax.swing.*;
import java.awt.*;

/**
 * @author jfoley.
 */
class InfoArea extends JLabel {
  public InfoArea(String s) {
    super(s);
    setMinimumSize(new Dimension(200, 16));
    setBackground(Color.WHITE);
  }
}

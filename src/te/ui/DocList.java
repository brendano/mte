package te.ui;
import java.awt.Color;
import java.awt.Component;
import java.util.*;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;

import te.data.Document;
import util.U;

public class DocList {
	JList<Document> jlist;
	JScrollPane scrollpane;
	DocSelectionListener queryReceiver;
	
	DocList(DocSelectionListener qr, List<Document> docsInDocidOrder) {
		Vector<Document> docOldVector = new Vector<Document>();
		for (Document d : docsInDocidOrder) docOldVector.add(d);
		jlist = new JList<>(docOldVector);
		jlist.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		jlist.setLayoutOrientation(JList.VERTICAL_WRAP);
		jlist.setVisibleRowCount(-1);
		jlist.setCellRenderer(new MyCellRenderer());
		scrollpane = new JScrollPane(jlist);
	}
	
	JComponent top() { return scrollpane; }
	
	class MyCellRenderer implements ListCellRenderer<Document> {

		@Override
		public Component getListCellRendererComponent(
				JList<? extends Document> list, Document doc, int index,
				boolean isSelected, boolean cellHasFocus) {

			JLabel jl = new JLabel(doc.docid);
			jl.setBorder(new EmptyBorder(1,2,1,2));
			jl.setOpaque(true);
			if (isSelected) {
				jl.setBackground(Color.BLUE);
				jl.setForeground(Color.WHITE);
			}
			else {
				jl.setBackground(Color.WHITE);
			}
			
			return jl;
		}
		
	}
	
}

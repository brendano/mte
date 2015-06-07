package te.ui.docview;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import com.google.common.eventbus.Subscribe;
import com.google.common.primitives.Ints;

import te.data.DocSet;
import te.data.Document;
import te.ui.GUtil;
import te.ui.My_DefaultListSelectionModel;
import te.ui.queries.AllQueries;
import te.ui.queries.DocSelectionChange;
import te.ui.queries.FulldocChange;
import te.ui.queries.TermQueryChange;
import util.U;

// TODO implement pushes to the query receiver

public class DocList {
	JList<Document> jlist;
	JScrollPane scrollpane;
	DocSelectionListener docselUpdateReceiver;
	public Consumer<Document> fulldocClickReceiver;
	ListSelectionListener listenerCallback = this::userChangesListSelection;
	
	public DocList(DocSelectionListener qr, List<Document> docsInOrderForDisplay) {
		docselUpdateReceiver = qr;
		Vector<Document> docOldVector = new Vector<Document>();
		for (Document d : docsInOrderForDisplay) docOldVector.add(d);
		jlist = new JList<>(docOldVector);
		jlist.setSelectionModel(new My_DefaultListSelectionModel());
		jlist.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		jlist.setLayoutOrientation(JList.VERTICAL_WRAP);
		jlist.setVisibleRowCount(-1);
		jlist.setCellRenderer(new MyCellRenderer());
		jlist.addMouseListener(new MyMouseListener());
		toggleListSelectionListener(true);
		scrollpane = new JScrollPane(jlist);
	}
	
	void toggleListSelectionListener(boolean enable) {
		if (enable) {
			assert jlist.getListSelectionListeners().length==0;
			jlist.addListSelectionListener(listenerCallback);
			assert jlist.getListSelectionListeners().length==1;
		} else {
			assert jlist.getListSelectionListeners().length==1;
			jlist.removeListSelectionListener(listenerCallback);
			assert jlist.getListSelectionListeners().length==0;
		}
	}
	
	public JComponent top() { return scrollpane; }
	
	class MyMouseListener extends MouseAdapter {
	    public void mouseClicked(MouseEvent e) {
	    	
	    	/* it looks like Swing sends us the mouse event AFTER the list selection event.
	    	can we assume that the AllQueries changes initiated by the list selection event are now finished?
	    	it depends whether or not the swing event loop is serial.
	    	if it's not all bets are off i think.
	    	while the AQ push/receives are all serial due to how EventBus works,
	    	the Swing event loop is controlling when the pushes happen.
	    	so i think it's not even guaranteed that if we push to AQ here, that the list selection event's AQ push is finished.
	    	um .. so let's racy assume it's ok to push to AQ now.
	    	*/
	    	
	    	if (e.getClickCount()==1) {
//	    		U.p("single click " + e);
	    	}
	    	else if (e.getClickCount()==2) {
//	    		U.p("double click " + e);
	            int index = jlist.locationToIndex(e.getPoint());
	            if (index==-1) {
	            	return;
	            }
	            if (index >= jlist.getModel().getSize()) {
	            	U.p("BAD INDEX in doc list " + index);
	            	return;
	            }
	            Document doc = jlist.getModel().getElementAt(index);
	            fulldocClickReceiver.accept(doc);
	    	}
	    }

	}
	
	public void userChangesListSelection(ListSelectionEvent e) {
//		U.pf("USERCHA curstate\t"); status();
		if (e.getValueIsAdjusting()) return;
		docselUpdateReceiver.receiveDocSelection(getDocidSelectionFromUIState());
	}
	
	List<String> getDocidSelectionFromUIState() {
		// this is a little indirect.  maybe we should use our own ListModel eventually.
		List<String> docids = new ArrayList<>();
		ListModel<Document> m = jlist.getModel();
		for (int i : jlist.getSelectedIndices()) {
			docids.add(m.getElementAt(i).docid);
		}
		return docids;
	}
	
	class MyCellRenderer implements ListCellRenderer<Document> {

		@Override
		public Component getListCellRendererComponent(
				JList<? extends Document> list, Document doc, int index,
				boolean isSelected, boolean cellHasFocus) {
			AllQueries AQ = AllQueries.instance();
			JLabel jl = new JLabel(doc.docid);
			jl.setBorder(new EmptyBorder(1,2,1,2));
			jl.setOpaque(true);
			if (isSelected) {
				jl.setBackground(AllQueries.highlightVersion(AQ.docPanelQueryColor));
			} else {
				jl.setBackground(Color.WHITE);
			}
			DocSet termDocs = AQ.termQuery().getMatchingDocs();
			if (termDocs.docsById.containsKey(doc.docid)) {
				jl.setForeground(AllQueries.foregroundVersion(AQ.termQueryColor));
			} else {
				jl.setForeground(Color.BLACK);
			}
			if (GUtil.nonnullEqual(doc.docid, AllQueries.instance().fulldocPanelCurrentDocID)) {
				jl.setBorder(new LineBorder(Color.BLACK, 2));
			}
			
			return jl;
		}
	}
	
	@Subscribe
	public void refreshFulldoc(FulldocChange e) {
		SwingUtilities.invokeLater(top()::repaint);
	}
	@Subscribe
	public void refreshTermQuery(TermQueryChange e) {
		SwingUtilities.invokeLater(top()::repaint);
	}
	
	void status() {
		U.pf("anchor %s, lead %s, min %s, max %s\n",
				jlist.getSelectionModel().getAnchorSelectionIndex(),
				jlist.getSelectionModel().getLeadSelectionIndex(),
				jlist.getSelectionModel().getMinSelectionIndex(),
				jlist.getSelectionModel().getMaxSelectionIndex()
		);
	}
	
	@Subscribe
	public void refreshNewDocSelection(DocSelectionChange e) {
//		U.pf("REFRESH curstate\t"); status();
		
		// Turn off list selection listeners for the duration of this function
		// really only for setSelectedIndices()
		toggleListSelectionListener(false);
		My_DefaultListSelectionModel m = (My_DefaultListSelectionModel) jlist.getSelectionModel();
		/* The funky logic of resetting the anchor/lead could be moved into the ListSelectionModel.
		 * in fact, the behavior we're working around here was reported as a bug and not fixed:
		 * https://bugs.openjdk.java.net/browse/JDK-4337119
		 * """
		 *    The current API of DefaultListSelectionModel doesn't allow us to set
		 *    the selection interval without affecting the lead/anchor indices.
		 * """
		 */
		int oldAnchor = m.getAnchorSelectionIndex();
		int oldLead = m.getLeadSelectionIndex();
				
		Set<String> seldocids = AllQueries.instance().docPanelSelectedDocIDs;
//		U.pf("REFRESH received %d docids: %s\n", seldocids.size(), seldocids);
		m.clearSelection();
		List<Integer> inds = new ArrayList<>();
		for (int i=0; i<jlist.getModel().getSize(); i++) {
			Document d = jlist.getModel().getElementAt(i);
			if (seldocids.contains(d.docid)) {
				inds.add(i);
			}
		}
		jlist.setSelectedIndices(Ints.toArray(inds));
		
		// look at old anchor/lead configuration and map to the new world.
		// are there cases where the lead and anchor are not necessarily the min/max of the selection?
		// for example, if the anchor is in the middle or something.
		// the solution here works with shift+arrowkeys so i guess it's good enough...?
		
		if (oldAnchor <= oldLead) {
			m.anchorIndex = m.getMinSelectionIndex();
			m.leadIndex = m.getMaxSelectionIndex();
		}
		else if (oldLead < oldAnchor) {
			m.leadIndex = m.getMinSelectionIndex();
			m.anchorIndex = m.getMaxSelectionIndex();
		}
		
		toggleListSelectionListener(true);
	}
}

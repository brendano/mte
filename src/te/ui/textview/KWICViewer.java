package te.ui.textview;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.DefaultCaret;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.eventbus.Subscribe;

import te.data.Corpus;
import te.data.DocSet;
import te.data.Document;
import te.data.TermInstance;
import te.ui.queries.AllQueries;
import te.ui.queries.DocSelectionChange;
import te.ui.queries.FulldocChange;
import te.ui.queries.TermQueryChange;
import util.Arr;
import util.U;
import util.misc.Pair;
import edu.stanford.nlp.util.Sets;

public class KWICViewer  {
	JPanel panel;
	JScrollPane scrollpane;
	List<KWICDocView> docviews;
	public Consumer<Document> fulldocClickReceiver;
	public BiConsumer<Document,TermInstance> fulldocTerminstClickReceiver;
	
	public int wordRadius = 5;
	private Set<String> termset;
	private List<Document> doclist;

	public KWICViewer() {
		panel = new JPanel();
		panel.setBackground(Color.white);
		panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));

        scrollpane = new JScrollPane(panel);
        scrollpane.getVerticalScrollBar().setUnitIncrement(10);
        scrollpane.setViewportView(panel);
	}
	
	public JComponent top() { return scrollpane; }
	
	static Font NORMAL_FONT, BOLD_FONT;
	static int fontHeight = 16;
	
	static { 
		NORMAL_FONT = new Font("Times", Font.PLAIN, fontHeight);
		BOLD_FONT = new Font("Times", Font.BOLD, fontHeight);
//		fontHeight = new JLabel(""){{ setFont(BOLD_FONT); }}.getGraphics().getFontMetrics().getHeight();
	}
	
	
	class KWICDocView extends JPanel {
		Document document;
		KWICDocView(Document doc, HitsResult r) { //List<WithinDocHit> hits) {
			document = doc;
			setBackground(Color.white);
			setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
			JPanel bla = new JPanel() {{ setLayout(new BoxLayout(this, BoxLayout.X_AXIS)); }};
			bla.add(new DocIDLabel(doc.docid));
			bla.add(new JLabel(String.format("  (%d instances)", r.totalHits)));
			bla.setBackground(Color.WHITE);
			add(bla);
//			add(new DocIDLabel(doc.docid));
			for (WithinDocHit h : r.hits) {
//				add(new JLabel(h.toString()));
				HitView hv = new HitView(doc, h);
				add(hv);
			}
			if (r.totalHits > r.hits.size()) {
				add(new JLabel(String.format("(%s shown, %s more)", r.hits.size(), r.totalHits - r.hits.size() )) {{ 
					setFont(new Font("SansSerif", Font.PLAIN, 10));
				}} );
			}
		}
		class DocIDLabel extends JLabel {
			public DocIDLabel(String docidtext) {
				super(docidtext);
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				addMouseListener(new MouseAdapter() {
					public void mouseClicked(MouseEvent e) {
						if (fulldocClickReceiver != null) {
							fulldocClickReceiver.accept(document);
						}
							
					}
				});
			}
			@Override public void paintComponent(Graphics _g) {
//				U.p("KWICDocView paint");
				super.paintComponent(_g);
				Graphics2D g = (Graphics2D) _g;
				String fulldocDocID = AllQueries.instance().fulldocPanelCurrentDocID;
				if (fulldocDocID!=null &&
						document.docid.equals(fulldocDocID)) {
					g.setColor(Color.black);
					g.drawRect(0,0,getWidth(),getHeight());
					g.drawRect(1,1,getWidth()-2,getHeight()-2);
				}
			}
		}
	}
	
	/** this is for one single hit -- i.e. one line in the kwic panel. */
	class HitView extends JPanel {
		String hitstr,leftstr,rightstr;
		Document doc;
		WithinDocHit dochit;

		HitView(Document d, WithinDocHit h) {
			doc=d;
			dochit=h;
			setLayout(null);
			setBackground(Color.white);
			hitstr = join(d, h.termStart,h.termEnd, " ");
			leftstr = join(d, Math.max(h.termStart-20, 0), h.termStart, " ") + " ";
			rightstr = " " + join(d, h.termEnd, Math.min(h.termEnd+20,d.tokens.size()), " ");
			setPreferredSize(new Dimension(200, fontHeight));
			setSize(new Dimension(200,fontHeight));
			
			addMouseListener(new MouseAdapter() {
				public void mouseClicked(MouseEvent e) {
					if (fulldocTerminstClickReceiver != null) {
						fulldocTerminstClickReceiver.accept(doc, getTermInstanceOfHit());
					}
				}
			});
		}
		
		TermInstance getTermInstanceOfHit() {
			List<TermInstance> terminsts = doc.tisByStartTokindex.get(dochit.termStart);
			assert terminsts!=null : "bad terminsts, is the index broken?";
			assert !terminsts.isEmpty() : "bad terminsts, is the index broken?";
			return terminsts.get(0);
		}

		@Override public String toString() {
			return String.format("HitView[%s dochit %s:%s]", Integer.toHexString(hashCode()),
					dochit.termStart, dochit.termEnd);
		}
		@Override
		public void paintComponent(Graphics _g) {
//			U.p("HitView paint");
			Graphics2D g = (Graphics2D) _g;
		    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
		    // text width calculators
			Function<String,Double> twCalc = (String s) -> (double) g.getFontMetrics(NORMAL_FONT).getStringBounds(s,g).getWidth(); 
			Function<String,Double> twBoldCalc = (String s) -> (double) g.getFontMetrics(BOLD_FONT).getStringBounds(s,g).getWidth();
			
			double withindocLeftMargin = 5;
			double withindocRightMargin = 5;
			double withindocWidth = scrollpane.getWidth() - withindocLeftMargin - withindocRightMargin;
//			double height = fontHeight;
			double cury = fontHeight-5;
			Rectangle2D oldClip = g.getClipBounds();
			g.setClip((int)withindocLeftMargin, 0, 
					(int) (oldClip.getWidth()- withindocLeftMargin), (int) oldClip.getHeight());
//			U.p("CLIP AFTER " + g.getClip());

//			double hittermWidth = twBoldCalc.apply(hitstr);
			double hittermWidth = twCalc.apply(hitstr);
			double hittermLeft = withindocLeftMargin + withindocWidth/2 - hittermWidth/2;
			double hittermRight = hittermLeft + hittermWidth;
			g.setFont(NORMAL_FONT);
			g.drawString(leftstr, (int) (hittermLeft - twCalc.apply(leftstr)), (int) cury);
			g.drawString(rightstr, (int) hittermRight, (int) cury);
//			g.setFont(BOLD_FONT);
			g.setColor(Color.BLUE);
			g.drawString(hitstr, (int) hittermLeft, (int) cury);
			g.setColor(Color.BLACK);
//			g.setFont(NORMAL_FONT);
		}
	}
	
	/** this should only be invoked on the swing event thread.
	 * http://stackoverflow.com/a/14940127/86684
	 * originally i was doing this on the eventbus thread, and was getting i guess a race condition that
	 * it sometimes went blank and swing didnt want to repaint it.
	 */
	void buildViews() {
		docviews = new ArrayList<>();
		for (Document d : doclist) {
			if (!Sets.intersects(d.termVec.support(), termset)) {
				continue;
			}
			HitsResult r = getHitsInDoc(d, termset, 500);
			docviews.add(new KWICDocView(d,r));
		}
		panel.removeAll();
		for (KWICDocView dv : docviews) {
			panel.add(dv);
		}
	}

	static String join(Document doc, int startIndex, int endIndex, String joiner) {
		return IntStream.range(startIndex,endIndex).mapToObj(j -> doc.tokens.get(j).text)
			.collect(Collectors.joining(joiner));
	}
	static String join(List<String> tokens, int startIndex, int endIndex, String joiner) {
		return IntStream.range(startIndex,endIndex).mapToObj(j -> tokens.get(j))
			.collect(Collectors.joining(joiner));
	}
	public void show(Collection<String> terms, DocSet docs) {
		doclist = new ArrayList<>(docs.docs());
		Collections.sort(doclist, Ordering.natural().onResultOf(d -> d.docid));
		termset = new HashSet<>(terms);
		SwingUtilities.invokeLater(this::buildViews);
	}
	
	
	static class WithinDocHit {
		// [inclusive,exclusive) token index span
		int termStart, termEnd;
	}
	
	static class HitsResult {
		List<WithinDocHit> hits = new ArrayList<>();
		int totalHits = 0;
	}
	
	HitsResult getHitsInDoc(Document d, Set<String> terms, int maxHitsWithinDoc) {
		HitsResult r = new HitsResult();
		
		// this doesnt use an index -- super slow!
		for (int i=0; i<d.tokens.size(); i++) {
			if ( ! d.tisByStartTokindex.containsKey(i)) continue;
			for (TermInstance ti : d.tisByStartTokindex.get(i)) {
				if (terms.contains(ti.termName)) {
					if (r.hits.size() < maxHitsWithinDoc) {
						WithinDocHit h = new WithinDocHit();
						h.termStart = ti.tokIndsInDoc.get(0);
						h.termEnd = ti.tokIndsInDoc.get( ti.tokIndsInDoc.size()-1 ) + 1;
						r.hits.add(h);
					}
					r.totalHits += 1;
				}
//				if (r.hits.size() >= maxHitsWithinDoc) break;
			}
//			if (r.hits.size() >= maxHitsWithinDoc) break;
		}
		Collections.sort(r.hits, Ordering.natural().onResultOf(h -> U.pair(h.termStart, h.termEnd)));
//		Collections.sort(hits, Comparator
//				.comparing((WithinDocHit h) -> h.termStart).thenComparing((WithinDocHit h) ->h.termEnd));
		return r;
	}

	///////////////////////////////////////////
	
	@Subscribe public void refresh(FulldocChange e) {
		SwingUtilities.invokeLater(top()::repaint);
	}
	
	@Subscribe public void refreshFull(DocSelectionChange e) { refreshFull(); }
	@Subscribe public void refreshFull(TermQueryChange e) { refreshFull(); }
	public void refreshFull() {
		DocSet curDS = AllQueries.instance().curDocs();
		show(AllQueries.instance().termQuery().terms, curDS);
	}
	

}

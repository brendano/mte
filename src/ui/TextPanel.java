package ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.DefaultCaret;

import jetbrains.HiDPIScaledGraphics;
import jetbrains.JBHiDPIScaledImage;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;

import util.Arr;
import util.U;
import util.misc.Pair;
import d.Corpus;
import d.DocSet;
import d.Document;
import d.TermInstance;
import edu.stanford.nlp.util.Sets;

public class TextPanel  {
	RawTextPanel rawarea;
	JScrollPane scrollpane;
	BufferedImage textbuffer;
	
	public int wordRadius = 5;
	private Set<String> termset;
	private List<Document> doclist;

	public TextPanel() {
        rawarea = new RawTextPanel();
        rawarea.setBackground(Color.white);
        rawarea.setFont(NORMAL_FONT);

        scrollpane = new JScrollPane(rawarea);
        scrollpane.setViewportView(rawarea);
	}
	
	public JComponent top() { return scrollpane; }
	
	static Font NORMAL_FONT, BOLD_FONT;
	
	static { 
		NORMAL_FONT = new Font("Times", Font.PLAIN, 16);
		BOLD_FONT = new Font("Times", Font.BOLD, 16);
	}
	
	@SuppressWarnings("serial")
	class RawTextPanel extends JPanel {
		@Override public void paintComponent(Graphics _g) {
			Graphics2D g = (Graphics2D) _g;
			super.paintComponent(g);  // not sure this is needed
			if (textbuffer != null) {
				AffineTransform t = new AffineTransform();
				t.scale(0.5,0.5);
				g.drawRenderedImage(textbuffer, t);	
			}
		}
	}
	
	static int MAX_VIRT_HEIGHT = 5000;
	
	void paintToTextBuffer() {
		textbuffer = new JBHiDPIScaledImage(rawarea.getWidth(), MAX_VIRT_HEIGHT, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = textbuffer.createGraphics();
		g.setColor(Color.white);
		g.fillRect(0,0, textbuffer.getWidth(), textbuffer.getHeight());
		paintTextToGraphics(g);
		g.dispose();
	}

	void paintTextToGraphics(Graphics2D g) {
		if (doclist==null || termset==null) return;
		g.setBackground(Color.white);
		g.setColor(Color.black);
		// the HiDPI system does this for us
//		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
//		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
//		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		double textHeight = g.getFontMetrics().getHeight();
		double lineSpacing = 2;
		double docSpacing = 5;
		double withindocLeftMargin = 20;
		double withindocRightMargin = 10;
		double withindocWidth = rawarea.getWidth() - withindocLeftMargin - withindocRightMargin;
		
		Function<String,Double> twCalc = (String s) -> (double) g.getFontMetrics(NORMAL_FONT).getStringBounds(s,g).getWidth(); 
		Function<String,Double> twBoldCalc = (String s) -> (double) g.getFontMetrics(BOLD_FONT).getStringBounds(s,g).getWidth();
		
//		double cury = docSpacing;
		double cury = 0;
		for (Document d : doclist) {
			if ( ! Sets.intersects(d.termVec.support(), termset)) {
				continue;
			}
			cury += docSpacing;
			cury += textHeight;
			g.setFont(NORMAL_FONT);
			g.drawString(d.docid, 0, Math.round(cury));

			g.setClip((int)withindocLeftMargin, 0, (int) withindocWidth, MAX_VIRT_HEIGHT);

			List<WithinDocHit> hits = getHitsInDoc(d, termset, 10);
			for (WithinDocHit h : hits) {
				String hitTerm = join(d, h.termStart,h.termEnd, " ");
				double hittermWidth = twBoldCalc.apply(hitTerm);
				String leftstr = join(d, Math.max(h.termStart-50, 0), h.termStart, " ") + " ";
				String rightstr = " " + join(d, h.termEnd, Math.min(h.termEnd+50,d.tokens.size()), " ");
				
				cury += lineSpacing;
				cury += textHeight;
				double hittermLeft = withindocLeftMargin + withindocWidth/2 - hittermWidth/2;
				double hittermRight = hittermLeft + hittermWidth;
				g.setFont(NORMAL_FONT);
				g.drawString(leftstr, (int) (hittermLeft - twCalc.apply(leftstr)), (int) cury);
				g.drawString(rightstr, (int) hittermRight, (int) cury);
				g.setFont(BOLD_FONT);
				g.drawString(hitTerm, (int) hittermLeft, (int) cury);
				g.setFont(NORMAL_FONT);
			}
			g.setClip(0,0,rawarea.getWidth(),MAX_VIRT_HEIGHT);
		}
		double newsize = cury+lineSpacing*3;
		rawarea.setPreferredSize(new Dimension(rawarea.getWidth(), (int) newsize));
		rawarea.setSize(new Dimension(rawarea.getWidth(), (int) (newsize)));
	}
	
	/** convention: [inc,exc) */
	static class Span {
		int start,end; 
		public Span(int s, int e) { start=s;end=e; }
		public String toString() { return U.sf("SPAN[%d,%d)",start,end); }
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
		paintToTextBuffer();
	}
	
	static class WithinDocHit {
		// [inclusive,exclusive)
		int termStart, termEnd;
	}
	
	List<WithinDocHit> getHitsInDoc(Document d, Set<String> terms, int maxHitsWithinDoc) {
		List<WithinDocHit> hits = new ArrayList<>();
		for (int i=0; i<d.tokens.size(); i++) {
			if ( ! d.tisByStartTokindex.containsKey(i)) continue;
			for (TermInstance ti : d.tisByStartTokindex.get(i)) {
				if (terms.contains(ti.termName)) {
					WithinDocHit h = new WithinDocHit();
					h.termStart = ti.tokIndsInDoc.get(0);
					h.termEnd = ti.tokIndsInDoc.get( ti.tokIndsInDoc.size()-1 ) + 1;
					hits.add(h);
				}
				if (hits.size() >= maxHitsWithinDoc) break;
			}
			if (hits.size() >= maxHitsWithinDoc) break;
		}
		Collections.sort(hits, Ordering.natural().onResultOf(h -> U.pair(h.termStart, h.termEnd)));
//		Collections.sort(hits, Comparator
//				.comparing((WithinDocHit h) -> h.termStart).thenComparing((WithinDocHit h) ->h.termEnd));
		return hits;
	}
}

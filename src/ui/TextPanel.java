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
//	JTextPane area;
	JEditorPane area;
	RawTextPanel rawarea;
	JScrollPane scrollpane;
	BufferedImage textbuffer;
	
	public int wordRadius = 5;
	private Set<String> termset;
	private List<Document> doclist;

	@SuppressWarnings("serial")
	public TextPanel() {
//		area = new JTextArea();
		area = new JEditorPane("text/html","");
        area.setEditable(false);
        area.setText("");

//        rawarea = new JPanel() {
//        	@Override
//        	public void paintComponent(Graphics g) {
//        		paintRawarea(this, (Graphics2D) g);
//        	}
//        };

        rawarea = new RawTextPanel();
        rawarea.setBackground(Color.white);
        rawarea.setFont(NORMAL_FONT);

        scrollpane = new JScrollPane(rawarea);
        scrollpane.setViewportView(rawarea);
        // http://stackoverflow.com/questions/3972337/java-swing-jtextarea-in-a-jscrollpane-how-to-prevent-auto-scroll
        DefaultCaret caret = (DefaultCaret)area.getCaret();
        caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
        
	}
	
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
//				double contextWidth = (withindocWidth - hittermWidth)/2; // for one side
//				double contextWidth = 1000;
//				Span left = getOnesideContext(d, h.termStart-1, -1, contextWidth, twCalc);
//				Span right = getOnesideContext(d, h.termEnd, +1, contextWidth, twCalc);
//				String leftstr = join(d, left.start,left.end, " ") + " ";
//				String rightstr = " " + join(d, right.start,right.end, " ");
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
		U.p("new size " + newsize);
		rawarea.setPreferredSize(new Dimension(rawarea.getWidth(), (int) (newsize)));
		rawarea.setSize(new Dimension(rawarea.getWidth(), (int) (newsize)));
	}
	
	/** convention: [inc,exc) */
	static class Span {
		int start,end; 
		public Span(int s, int e) { start=s;end=e; }
		public String toString() { return U.sf("SPAN[%d,%d)",start,end); }
	}
	
	static Span getOnesideContext(Document d, int firstTokenIndexClosestToHit, int direction, double maxWidth, 
			Function<String,Double> textWidthCalc) {
		assert maxWidth > 0;
		StringBuilder s = new StringBuilder();

		int curtok = firstTokenIndexClosestToHit;
		int numtok = 1;
		s.append(" ");
		s.append(d.tokens.get(curtok).text);
		
		while (curtok>=0 && curtok < d.tokens.size() && numtok < 1000 
				&& textWidthCalc.apply(s.toString()) < maxWidth) {
			curtok += direction;
			numtok++;
			s.append(" ");
			s.append(d.tokens.get(curtok).text);
//			U.p("{{" + s.toString()+"}} " + textWidthCalc.apply(s.toString()));
		}
		
		curtok -= direction;
		
		// now we have [curtok,rightmostTokenIndex] if we were going left
		// or the other way if going right .. i think.
		Span ret = new Span( 
				Math.min(curtok, firstTokenIndexClosestToHit),
				Math.max(curtok, firstTokenIndexClosestToHit) +1);
		return ret;
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
		U.p("UPDATE DOCS");
		doclist = new ArrayList<>(docs.docs());
		Collections.sort(doclist, Ordering.natural().onResultOf(d -> d.docid));
		termset = new HashSet<>(terms);

		paintToTextBuffer();
//		renderHTML();
	}
	void renderHTML() {
		StringBuilder s = new StringBuilder();
		for (Document d : doclist) {
			if ( ! Sets.intersects(d.termVec.support(), termset)) {
				continue;
			}
			s.append(U.sf("%s\n", d.docid));
			s.append(passageReportHTML(d, termset));
			s.append("\n");
		}
		String finalstr = s.toString().replace("\n","<br>");
		area.setText(finalstr);
//		scrollpane.getVerticalScrollBar().setValue(0);
	}
	
	static class WithinDocHit {
		// [inclusive,exclusive)
		int spanStart, spanEnd;
		int termStart, termEnd;
	}
	
	StringBuilder passageReportHTML(Document d, Set<String> terms) {
		List<WithinDocHit> hits = getHitsInDoc(d, terms, 10);
		return makeHTML(d, hits);
	}

	StringBuilder makeHTML(Document d, List<WithinDocHit> hits) {
		StringBuilder s = new StringBuilder();
		for (WithinDocHit h : hits) {
			assert h.spanStart<=h.termStart && h.termStart<=h.termEnd && h.termEnd <= h.spanEnd;
			s.append("&nbsp; -");
			for (int j=h.spanStart; j<h.spanEnd; j++) {
				s.append(" ");
				if (j==h.termStart) s.append("<b>");
				String w = d.tokens.get(j).text;
				w = htmlEscape(w);
				s.append(w);
				if (j+1==h.termEnd) s.append("</b>"); 
			}
			s.append("\n");
		}
		return s;
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
					h.spanStart = Math.max(h.termStart-wordRadius, 0);
					h.spanEnd = Math.min(h.termEnd+wordRadius, d.tokens.size()); 
					hits.add(h);
				}
				if (hits.size() >= maxHitsWithinDoc) break;
			}
			if (hits.size() >= maxHitsWithinDoc) break;
		}
		
		Collections.sort(hits, Ordering.natural().onResultOf(h -> U.pair(h.spanStart, h.termStart)));
		return hits;
	}
	static String htmlEscape(String s) {
		return s.replace("<","&lt;").replace(">","&gt;").replace("&","&amp;");
	}
}

package te.ui.textview;


import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import scrolltest.MyScrollpane;
import te.data.Document;
import te.data.NLP;
import te.data.Span;
import te.data.TermInstance;
import te.data.Token;
import te.ui.GUtil;
import util.Arr;
import util.BasicFileIO;
import util.U;
import util.misc.Pair;

import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import edu.stanford.nlp.util.MutableInteger;

public class MyTextArea {
	InternalTextArea area;
	JScrollPane scrollpane;
	Document doc;
	/** this function lazily specifies in-text highlights at the term level.
	 * if it's set to null, means dont highlight anything.
	 * returns null means dont highlight this term. 
	 * eventually we'll want to return a style object, not just a color.
	 */
	Function<TermInstance, Color> termHighlighter;
	public JComponent top() { return scrollpane; }

	/* rendering model.  no extra paragraph spacing. thus screen lines are all that matters.
	 * note number of paragraphs (physical lines) is the lower bound on the number of screen lines.
	 * some paragraphs have to wrap. */
	
	List<Span> paragraphSpans;  // aka physical lines from file .. charspans.

	static int FONT_HEIGHT = 14;
	static Font NORMAL_FONT = new Font("Times", Font.PLAIN, FONT_HEIGHT);
	
	int getLineHeight() {
		Graphics tmpg = area.getGraphicsConfiguration().createCompatibleVolatileImage(200,10).getGraphics();
		int h = getLineHeight(tmpg);
		tmpg.dispose();
		return h;
	}
	int getLineHeight(Graphics g) {
		int fontHeight = g.getFontMetrics(NORMAL_FONT).getHeight();
		return fontHeight;
//		return (int) Math.ceil(fontHeight*1.0);
	}

	/** this can't handle hard breaks. only infers soft breaks. */
	public static List<Integer> calculateBreaks(Document doc, int charstart, int charend, int width, Function<String,Integer> widthMeasure) {
		List<Integer> possBreaks = possibleBreakpoints(doc, charstart, charend);
		if (possBreaks.size()>0) assert possBreaks.get(possBreaks.size()-1) != doc.text.length();
		possBreaks.add(charend);
		int widthLeft = width;
		int curStart=0;
		List <Integer> breaks = new ArrayList<>();
		
		for (int possBreak : possBreaks) {
			String cand = doc.text.substring(curStart,possBreak);
			int w = widthMeasure.apply(cand);
//			U.pf("W=%3d  %d:%d  CAND [[%s]]\n", w, curStart, possBreak, cand.replace("\n", " "));
			if (w > widthLeft) {
				if (curStart>0) {
					breaks.add(curStart);
				}
				widthLeft = width - w;
			} else {
				widthLeft -= w;
			}
			curStart = possBreak;
		}
		return breaks;
	}
	
	public static List<Integer> possibleBreakpoints(Document doc, int charStart, int charEnd) {
		// uses tokenization and stuff
		charEnd = Math.min(charEnd, doc.text.length());
		assert charStart >= 0;
		int curchar = charStart;
		int curtok = doc.getIndexOfFirstTokenAtOrAfterCharIndex(charStart);
		List<Integer> breaks = new ArrayList<>();
		
		if (curtok==-1) {
			while (curchar < charEnd) {
				breaks.add(curchar);
				curchar++;
			}
			return breaks;
		}
		int numiter=0;
		while(true) {
			while (curchar<charEnd && curchar <= doc.tokens.get(curtok).startChar) {
				breaks.add(curchar);
				curchar++;
			}
			curchar = doc.tokens.get(curtok).endChar;
			curtok++;
			if (curchar>=charEnd) {
				return breaks;
			}
			if (curtok>=doc.tokens.size()) {
				while (curchar <  charEnd) {
					breaks.add(curchar);
					curchar++;
				}
				return breaks;
			}
			if (++numiter > (int) 100e6) {
				assert false : "bug in possible breakpoints";
			}
		}
	}
	
	/** a Rendering is legitimate for a specific desired rending width */
	static class Rendering {
		List<Span> screenlineCharSpans = new ArrayList<>();
		int totalScreenLines=0;
	}
	
	/** can fail with null if thread is interrupted */
	Rendering renderWordWrapping(int width) {
		if (doc==null) return null;
		Rendering r = new Rendering();
		if (paragraphSpans==null) return r;

		FontMetrics fm = new Canvas(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration()).getFontMetrics(NORMAL_FONT);
		
		for (int p=0; p<paragraphSpans.size(); p++) {
			if (Thread.interrupted()) { return null; }
			Span pspan = paragraphSpans.get(p);
			List<Integer> softbreaks = calculateBreaks(doc, pspan.start, pspan.end, width, fm::stringWidth);
			List<Span> screenlineCharSpans = GUtil.breakpointsToSpans(pspan.start, softbreaks, pspan.end);
			for (Span scs : screenlineCharSpans) {
				if (Thread.interrupted()) { return null; }
				r.screenlineCharSpans.add(scs);
//				U.pf("%10s ||| %s\n", scs, GUtil.substring(doc.text, scs).replace("\n","[N]").replace(" ","[S]"));
			}
		}
		r.totalScreenLines = r.screenlineCharSpans.size();
		return r;
	}
	public void requestScrollToTerminst(TermInstance ti) {
		if (!renderIsReady) {
			termToScrollToWhenRenderIsDone = ti;
			return;
		}
		else {
			scrollToTerminst(ti, finishedRendering);	
		}
	}
	/** don't call this unless rendering is complete */
	private  void scrollToTerminst(TermInstance ti, Rendering r) {
		int ci = doc.tokens.get(ti.tokIndsInDoc.get(0)).startChar;
		scrollToCharindex(ci, r);
	}
	/** don't call this unless rendering is complete */
	private void scrollToCharindex(int ci, Rendering r) {
//		U.p("searching for charindex " + ci);
		OptionalInt screenLine = IntStream.range(0,r.totalScreenLines)
				.filter((si) -> r.screenlineCharSpans.get(si).contains(ci))
				.findFirst();
		if (!screenLine.isPresent()) {
			// wasn't able to find one? weird.
			U.p("couldnt find matching screenline containing charindex " + ci);
			return;
		}
		int si = screenLine.getAsInt();
//		U.p("screenline " + si);
		
		int y = (int) ((double) baselineYvalueForScreenline(si) - 0.5*getLineHeight());
		int topShouldBe = y - scrollpane.getHeight()/2;
		topShouldBe = GUtil.bounded(topShouldBe, 0, area.getHeight());
		area.scrollRectToVisible(new Rectangle(0, topShouldBe, scrollpane.getWidth(), scrollpane.getHeight()));
	}
	
	/* Here's the model:
	 * a resize launches a new text-rendering thread.
	 * once it's done, then it asks swing for a new repaint.  at that time, painting will be done better.
	 * 
	 * when doing many resizes, we dont want to launch 10 or 20 simultaneous text rendering theads.
	 * so: at any time, only one text-rendering thread should be active.
	 * the latest one gets priority.
	 * this is implemented by killing the currently running thread (well, using Thread.interrupt bcs that's supposed to be safer).
	 * finally, there's a lock around the thread management and launching code, which hopefully should be pretty quick.  it's the render that's potentially slow.
	 */

	Rendering finishedRendering = null;
	Thread rerenderThread = null;
	TermInstance termToScrollToWhenRenderIsDone = null;
	Object renderThreadManagementLock = new Object();
	boolean renderIsReady = false;
	
	void launchTextRender(final boolean isNewDoc) {
		// take the current width as given, then compute a new height to force it to.
		long t0 = System.nanoTime();
		
		synchronized(renderThreadManagementLock) {
			renderIsReady = false;
			if (rerenderThread!=null && rerenderThread.isAlive()) {
//				U.p("trying to kill thread");
				rerenderThread.interrupt();
			}
			Thread t = new Thread( () -> {
				int w = area.getWidth();
//				U.pf("w=%d start render thread\n", w);
				Rendering r = renderWordWrapping(w);  // here is the expensive call
				if (r==null) return;
				// should the last lines here be wrapped in a sync block?
				int newHeight = (r.totalScreenLines + 1)* getLineHeight();
				area.setPreferredSize(new Dimension(-1, newHeight));
//				U.pf("w=%d textflow %.2f ms\n", w, (System.nanoTime()-t0)/1e6);
				area.revalidate();
				if (Thread.interrupted()) return;
				if (termToScrollToWhenRenderIsDone != null) {
					scrollToTerminst(termToScrollToWhenRenderIsDone, r);
					termToScrollToWhenRenderIsDone = null;
				} else if (isNewDoc) {
					scrollpane.getVerticalScrollBar().setValue(0);
				}
				finishedRendering = r;
				renderIsReady = true;
			});
			rerenderThread = t;
			t.start();
		}
	}
	
	int baselineYvalueForScreenline(int screenline) {
		return (screenline + 1) * getLineHeight();
	}

	/** draw in the clipping region based on the current text rendering. */
	void draw(Graphics2D g, int width) {
		long t0 = System.nanoTime();

		Rectangle clip = g.getClipBounds();
		Rendering rend = finishedRendering;
		if (rend==null) {
//			U.p("exit draw() early");
			return;
		}
		
		int lineHeight = getLineHeight();
		g.setColor(Color.WHITE);
		g.fillRect(clip.x,clip.y,clip.width,clip.height);
		g.setColor(Color.BLACK);
	    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
	    g.setFont(NORMAL_FONT);
	    
	    int firstline = clip.y / lineHeight;
	    int lastline = (clip.y + clip.height) / lineHeight;
	    firstline--; firstline=Math.max(firstline,0);
	    lastline++; lastline=Math.min(lastline, rend.totalScreenLines-1);
//	    U.pf("screenlines %d..%d\n", firstline,lastline);
	    
	    for (int line=firstline; line<=lastline; line++) {
	    	Span span = rend.screenlineCharSpans.get(line);
	    	drawTextInSpan(span, g, 0, (line+1)*lineHeight);
	    }
//		U.pf("END draw %.2f ms\n", (System.nanoTime()-t0)/1e6);
	}
	
	/** ASSUME this is all within one screenline. */
	@SuppressWarnings("unchecked")
	private void drawTextInSpan(Span charspanToDraw, Graphics2D g, int x, int y) {
		// simple solution: just draw the damn text
//    	String str = GUtil.substring(doc.text, charspanToDraw);
//    	g.drawString(str, x, y);
    	
    	// more complex: check tokens for highlighting. also have to draw non-token gaps.
		FontMetrics fm = g.getFontMetrics(NORMAL_FONT);
    	int[] tis = getTokenIndexesInSpan(doc, charspanToDraw);
    	
    	// 1. nontoken segment before first token, if any
    	// 2. each token
    	// 3.   plus nontoken stuff after it, if any, and not going past the span bound
    	int curx=x;
    	// 1. nontoken segment before first token
    	// this span should NOT include any tokens. if it does, that was a bug in the word wrap render code.
    	if (tis.length>0) {
    		int firstTokStart = doc.tokens.get(tis[0]).startChar;
    		if (firstTokStart > charspanToDraw.start) {
    			String s = doc.text.substring(charspanToDraw.start, firstTokStart);
    			g.drawString(s, curx, y);
    			curx += fm.stringWidth(s);
    		}
    	}
    	// 2. each token 3. nontoken segment after token 
    	// this code assumes tis are consecutive, or at least ordered?...
    	for (int i=0; i<tis.length; i++) {
    		int toki = tis[i];
    		// 2. the token
    		Token tok = doc.tokens.get(toki);
    		int charstart = tok.startChar;
    		int charend = tok.endChar;
    		String s = doc.text.substring(charstart, charend);
    		
    		// check for term-level things.
    		Color color = Color.BLACK;
    		if (termHighlighter != null) {
        		for (TermInstance terminst : doc.tisByAllTokindexes.getOrDefault(toki, Collections.EMPTY_LIST)) {
        			Color cc = termHighlighter.apply(terminst);
        			if (cc != null) {
        				color=cc;
        			}
        		}
    		}
    		// ok draw it now
    		g.setColor(color);
    		g.drawString(s, curx, y);
    		curx += fm.stringWidth(s);

    		// 3. nontoken segment after the token
    		g.setColor(Color.BLACK);
    		charstart = charend;
    		if (i < tis.length-1) {
    			int nexttoki = tis[i+1];
    			charend = doc.tokens.get(nexttoki).startChar;
    		} else {
    			// this is the last token.  the span from the end of this token to the end of the to-draw span should all be nontoken characters.
    			charend = charspanToDraw.end;
    		}
    		s = doc.text.substring(charstart, charend);
    		g.drawString(s, curx, y);
    		curx += fm.stringWidth(s);
    	}
    	
	}
	static int[] getTokenIndexesInSpan(Document d, Span charspan) {
		// should use indexing to be faster on long documents
		return IntStream.range(0, d.tokens.size())
				.filter(ti -> {
					Token t = d.tokens.get(ti);
					return GUtil.spanContainedIn(t.startChar, t.endChar, charspan); 
				}).toArray();
	}
	static List<Token> getTokensInSpan(Document d, Span charspan) {
		return d.tokens.stream().filter(t -> GUtil.spanContainedIn(t.startChar, t.endChar, charspan))
				.collect(Collectors.toList());
	}
	void setDocument(Document newdoc) {
		if (this.doc==newdoc) return;
		this.doc = newdoc;
		if (newdoc!=null) loadDocumentIntoRenderingDatastructures();
	}
	void loadDocumentIntoRenderingDatastructures() {
		assert doc!=null : "document must be set before calling this";
		paragraphSpans = GUtil.splitIntoSpans("\n", doc.text);
//		for (Span s : paragraphSpans) {
//			U.pf("%10s ||| %s\n", s, GUtil.substring(doc.text, s).replace("\n","[N]").replace(" ","[S]"));
//		}
	}
	
	class InternalTextArea extends JPanel {
		InternalTextArea() {
//			setPreferredSize(new Dimension(200,-1));
			addComponentListener(new ComponentAdapter() {
				@Override public void componentResized(ComponentEvent e)  {
					launchTextRender(false);
				}
			});
		}
		
		@Override public void paintComponent(Graphics _g) {
			Graphics2D g = (Graphics2D) _g;
//			U.pf("SIZE %s  CLIP %s  VIEWABLE %s\n", getSize(), g.getClip(), getVisibleRect());
			draw(g, getWidth());
		}
	}
	
	public MyTextArea() {
		area = new InternalTextArea();
//		scrollpane = new MyScrollpane(area);
		scrollpane = new JScrollPane(area);
		scrollpane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		scrollpane.getVerticalScrollBar().setUnitIncrement(10);
//		scrollpane.getVerticalScrollBar().setBlockIncrement(10);
//		scrollpane.setViewportView(area);
	}
 	
	public static void main(String[] args) throws IOException {
		Document d = new Document();
		d.text = BasicFileIO.readFile(System.in);
		d.tokens = NLP.simpleTokenize(d.text);
		U.p(d.tokens);
		
		JFrame main = new JFrame() {{ setSize(new Dimension(200,500)); }};
		MyTextArea a = new MyTextArea();
		a.doc = d;
		a.loadDocumentIntoRenderingDatastructures();
		main.add(a.top());
		main.setVisible(true);
	}
}

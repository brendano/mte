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

import te.data.Document;
import te.data.NLP;
import te.data.Span;
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
	
	public static List<Integer> possibleBreakpoints(Document doc) {
		return possibleBreakpoints(doc, 0, doc.text.length());
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
		Rendering r = new Rendering();

		// need to dispose this tiny temp graphics object?
//		FontMetrics fm = tmpg.getFontMetrics(NORMAL_FONT);
		FontMetrics fm = new Canvas(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration()).getFontMetrics(NORMAL_FONT);
		
		for (int p=0; p<paragraphSpans.size(); p++) {
			if (Thread.interrupted()) { return null; }
			Span pspan = paragraphSpans.get(p);
			List<Integer> softbreaks = calculateBreaks(doc, pspan.start, pspan.end, width, fm::stringWidth);
			int nl = softbreaks.size() + 1;
			List<Span> screenlineCharSpans = GUtil.breakpointsToSpans(pspan.start, softbreaks, pspan.end);
			for (Span scs : screenlineCharSpans) {
				r.screenlineCharSpans.add(scs);
			}
		}
		r.totalScreenLines = r.screenlineCharSpans.size();
		return r;
	}
	

	Rendering _rendering = null;
	Thread rerenderThread = null;
	Object renderLock = new Object();
	
	void setRendering(Rendering r) {
		_rendering = r;
	}
	
	void launchTextRender() {
		// take the current width as given, then compute a new height to force it to.
		long t0 = System.nanoTime();
		
		synchronized(renderLock) {
			if (rerenderThread!=null && rerenderThread.isAlive()) {
//				U.p("trying to kill thread");
				rerenderThread.interrupt();
			}
			Thread t = new Thread( () -> {
				int w = area.getWidth();
//				U.pf("w=%d start render thread\n", w);
				Rendering r = renderWordWrapping(w);
				if (r==null) return;
				_rendering = r;
				int newHeight = (_rendering.totalScreenLines + 1)* getLineHeight();
				area.setPreferredSize(new Dimension(-1, newHeight));
//				U.pf("w=%d textflow %.2f ms\n", w, (System.nanoTime()-t0)/1e6);
				top().repaint();
			});
			rerenderThread = t;
			t.start();
		}
		
	}
	
	Rendering getRendering() {
		if (_rendering != null) return _rendering;
		return renderWordWrapping(area.getWidth());
	}
	
	void draw(Graphics2D g, int width) {
		// need to supply the width.  this function determines the height.
		long t0 = System.nanoTime();

		Rectangle clip = g.getClipBounds();
		Rendering rend = _rendering;
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
	    	String str = GUtil.substring(doc.text, span);
	    	g.drawString(str, 0, (line+1)*lineHeight);
	    }
//		U.pf("END draw %.2f ms\n", (System.nanoTime()-t0)/1e6);
	}

	void loadDocumentIntoRenderingDatastructures() {
		assert doc!=null : "document must be set before calling this";
		paragraphSpans = GUtil.splitIntoSpans("\n", doc.text);
	}
	
	class InternalTextArea extends JPanel {
		InternalTextArea() {
//			setPreferredSize(new Dimension(200,-1));
			addComponentListener(new ComponentAdapter() {
				@Override public void componentResized(ComponentEvent e)  {
					launchTextRender();
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
		scrollpane = new JScrollPane(area);
		scrollpane.getVerticalScrollBar().setUnitIncrement(20);
//		scrollpane.getVerticalScrollBar().setBlockIncrement(10);
		scrollpane.setViewportView(area);
	}
 	
	public static void main(String[] args) throws IOException {
		Document d = new Document();
		d.text = BasicFileIO.readFile(System.in);
		d.tokens = NLP.stanfordTokenize(d.text);
//		U.p(d.tokens);
		
		JFrame main = new JFrame() {{ setSize(new Dimension(200,200)); }};
		MyTextArea a = new MyTextArea();
		a.doc = d;
		a.loadDocumentIntoRenderingDatastructures();
		main.add(a.top());
		main.setVisible(true);
	}
}

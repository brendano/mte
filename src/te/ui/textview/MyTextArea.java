package te.ui.textview;


import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.function.*;

import te.data.Document;
import te.data.NLP;
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
	
	List<String> paragraphs;  // aka physical lines from file

	static int FONT_HEIGHT = 14;
	static Font NORMAL_FONT = new Font("Times", Font.PLAIN, FONT_HEIGHT);
	
	int getLineHeight(Graphics g) {
		int fontHeight = g.getFontMetrics(NORMAL_FONT).getHeight();
		return (int) Math.ceil(fontHeight*1.0/2);
	}
	
	class Rendering {
		int[] screenLinesPerParagraph; // parallel to the above. depends on rendering width.
		Image image;
	}

	Pair<Image,Graphics2D> createNewSegment(Function<Integer,Image> fn, int width, int height) {
		Image image = fn.apply(height);
		Graphics2D g = (Graphics2D) image.getGraphics();
		g.setColor(Color.WHITE);
		g.fillRect(0,0,width,height);
		g.setColor(Color.BLACK);
	    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
	    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
	    g.setFont(NORMAL_FONT);
		return U.pair(image, g);
	}
	
	static boolean isNonprinting(char c) {
		// this is complicated. depends what it's aimed for.
		if (c=='\n') return false;
		return true;
	}
	
	static class Break {
		int charind;  // for the insertion point. break right before this character.
		boolean isSoftBreak;  // otherwise it's a hard break
	}
	
	static List<Integer> calculateBreaks(Document doc, int width, Function<String,Integer> widthMeasure) {
		List<Integer> possBreaks = possibleBreakpoints(doc);
		if (possBreaks.size()>0) assert possBreaks.get(possBreaks.size()-1) != doc.text.length();
		possBreaks.add(doc.text.length());
		int widthLeft = width;
		int curStart=0;
		List <Integer> breaks = new ArrayList<>();
		
		for (int possBreak : possBreaks) {
			String cand = doc.text.substring(curStart,possBreak);
			int w = widthMeasure.apply(cand);
			if (w > widthLeft) {
				if (curStart>0) breaks.add(curStart);
				widthLeft = width - w;
			} else {
				widthLeft -= w;
			}
			curStart = possBreak;
		}
		return breaks;
	}

	
	static List<Integer> possibleBreakpoints(Document doc) {
		// uses tokenization and stuff
		int curtok = 0;
		int curchar = 0;
		List<Integer> breaks = new ArrayList<>();
		while(true) {
			while (curchar <= doc.tokens.get(curtok).startChar) {
				breaks.add(curchar);
				curchar++;
			}
			curchar = doc.tokens.get(curtok).endChar;
			curtok++;
			if (curchar>=doc.text.length()) break;
			if (curtok>=doc.tokens.size()) {
				while (curchar < doc.text.length()) {
					breaks.add(curchar);
					curchar++;
				}
				break;
			}
		}
		return breaks;
	}
	
	/** calculate all visible break positions for a given rendering width and font.
	 * includes both softbreaks (ones caused by wordwrap) as well as hardbreaks (forced by newlines)
	 */
	static List<Integer> calculateBreaks2(Document doc, int width, FontMetrics fm) {
		return calculateBreaks(doc, width, fm::stringWidth);
	}
	
	static List<Integer> calculateBreaks2(Document doc, int width, Function<String,Integer> widthMeasure) {
		List<Integer> possBreaks = possibleBreakpoints(doc);
		possBreaks.add(doc.text.length());
		List<Integer> breaks = new ArrayList<>();
		int curStart = 0;
		int lastPossBreakThatBreaked = -1;
		
//		for (int i=0; i<possBreaks.size(); i++) {
		int i=0;
		while(true) {
			int possEnd = possBreaks.get(i);
			if (possEnd < doc.text.length()-1 && doc.text.charAt(possEnd)=='\n') {
				breaks.add(possEnd);
				curStart=possEnd;
				lastPossBreakThatBreaked = possEnd;
			}
			else {
				assert curStart<=possEnd;
				String candidateLine = doc.text.substring(curStart, possEnd-curStart);
				U.pf("CAND %d:%d [[%s]]\n", curStart,possEnd, candidateLine);
				int w = widthMeasure.apply(candidateLine);
				if (w > width && (i==0 || lastPossBreakThatBreaked==possBreaks.get(i))) {
					// this is just a really long token that has to be too wide
					breaks.add(possEnd);
					curStart=possEnd;
					lastPossBreakThatBreaked = possEnd;
				}
				else if (w > width && curStart<possEnd) {
					// we've gone over the desired width.
					// use the last previous breakpoint candidate as the breakpoint.
					int lastbreak = possBreaks.get(i-1);
					breaks.add(lastbreak);
					curStart = lastbreak;
					lastPossBreakThatBreaked = lastbreak;
					i = i-1; // so next iteration we'll redo considering the current breakpoint.
				}
			}
		}
		// is this possible now? not sure
		if (breaks.size()>0 && breaks.get( breaks.size()-1 ) == doc.text.length()) {
			assert false : "Wtf";
			throw new RuntimeException("wtf");
		}
		return breaks;
	}
	
	Rendering render(int width) {
		// need to supply the width.  this function determines the height.
		long t0 = System.nanoTime(); U.pf("\n");

		Rendering r = new Rendering();
		r.screenLinesPerParagraph = new int[paragraphs.size()];
		// createCompatibleImage() is recommended by Haase, but on Retina displays it displays text crappily - maybe it uses a naive scaler or something.
		// createCompatibleVolatileImage() works fine.
		// seen in http://comments.gmane.org/gmane.comp.java.openjdk.macosx-port.devel/6400
		// should use bufferedimage on windows?
		Function<Integer,Image> makeImage = (Integer h) -> area.getGraphicsConfiguration().createCompatibleVolatileImage(width, h);
//		Function<Integer,Image> makeImage = (Integer h) -> area.getGraphicsConfiguration().createCompatibleImage(width, h);

		int height = 100;
		double multiplier = 1.5;
		Pair<Image,Graphics2D> pair = createNewSegment(makeImage, width, height);
		Graphics2D curg = pair.second;
		Image curimg = pair.first;
		Image newimg = null;
		Graphics2D newg = null;
	    int lineHeight = getLineHeight(curg);

	    for (int linenum=0; linenum < paragraphs.size(); linenum++) {
	    	int absoluteY = (linenum+1) * lineHeight;
	    	if (absoluteY > height-40) {
	    		U.pf("startcopy %.2f ms\n", (System.nanoTime()-t0)/1e6);
	    		// cleanup current
	    		curg.dispose();
	    		// make new
	    		int newHeight = (int) (multiplier * height);
	    		U.pf("absy %s  oldheight %s  mult %s  newheight %s\n", absoluteY, height, multiplier, newHeight);
	    		assert absoluteY <= newHeight;
	    		pair = createNewSegment(makeImage, width, newHeight);
	    		newimg = pair.first;
	    		newg = pair.second;
	    		newg.drawImage(curimg,0,0,null);
	    		newg.drawLine(0,newHeight,width,newHeight);
	    		curimg=newimg;
	    		curg=newg;
	    		height=newHeight;
	    		U.pf("copyend %.2f ms\n", (System.nanoTime()-t0)/1e6);
	    	}

	    	
	    	String line = paragraphs.get(linenum);
//	    	line = line.substring(0,Math.min(line.length(),100));
    		curg.drawString(line, 0, absoluteY);
	    	int ww = curg.getFontMetrics(NORMAL_FONT).stringWidth(line);
//	    	curg.drawLine(0,absoluteY,ww,absoluteY);
			linenum++;
		}
	    
	    // finish the current segment
	    curg.dispose();
		r.image = curimg;
		Arr.fill(r.screenLinesPerParagraph, 1);
		U.pf("END time %.2f ms\n", (System.nanoTime()-t0)/1e6);
//		JTextArea
		return r;
	}
	
	void loadDocumentIntoRenderingDatastructures() {
		assert doc!=null : "document must be set before calling this";
		paragraphs = new ArrayList<>();
		for (String line : doc.text.split("\n")) {
			paragraphs.add(line);
		}
	}
	class InternalTextArea extends JPanel {
		InternalTextArea() {
//			setPreferredSize(new Dimension(200,-1));
		}
		
		@Override public void paintComponent(Graphics _g) {
			Graphics2D g = (Graphics2D) _g;
			U.pf("SIZE %s  CLIP %s  VIEWABLE %s\n", getSize(), g.getClip(), getVisibleRect());
			Image img = render(getWidth()).image;
			g.drawImage(img,0,0,null);
		}
	}
	
	public MyTextArea() {
		area = new InternalTextArea();
		scrollpane = new JScrollPane(area);
		scrollpane.setViewportView(area);
	}
 	
	public static void main(String[] args) throws IOException {
		Document d = new Document();
		d.text = BasicFileIO.readFile(System.in);
		d.tokens = NLP.stanfordTokenize(d.text);
		
		U.p(possibleBreakpoints(d));
		
		JFrame main = new JFrame() {{ setSize(new Dimension(200,200)); }};
		MyTextArea a = new MyTextArea();
		a.doc = d;
		a.loadDocumentIntoRenderingDatastructures();
		main.add(a.top());
		main.setVisible(true);
	}
}

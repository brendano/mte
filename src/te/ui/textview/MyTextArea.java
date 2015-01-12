package te.ui.textview;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import te.data.Document;
import te.data.NLP;
import te.ui.GUtil;
import util.BasicFileIO;
import util.U;
import util.misc.Pair;

import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;

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
	Rendering render(int width) {
		// need to supply the width.  this function determines the height.

		Rendering r = new Rendering();
		r.screenLinesPerParagraph = new int[paragraphs.size()];
		int segmentPixelHeight = 100;
		int segmentExtraForBuffer = 30;
		int segmentExtraForLine = 4;
		
		List<Image> segmentImages = new ArrayList<>();
		List<Integer> segmentHeights = new ArrayList<>();
		
		// createCompatibleImage() is recommended by Haase, but on Retina displays it displays text crappily - maybe it uses a naive scaler or something.
		// createCompatibleVolatileImage() works fine.
		// seen in http://comments.gmane.org/gmane.comp.java.openjdk.macosx-port.devel/6400
		Function<Integer,Image> makeImage = (Integer h) -> area.getGraphicsConfiguration().createCompatibleVolatileImage(width, h);
		
		Pair<Image,Graphics2D> pair = createNewSegment(makeImage, width,segmentPixelHeight);
		Graphics2D g = pair.second;
		Image curimg = pair.first;
		int usedHeightInCurrentSegment = 0;
		int absoluteHeightAtCurrentY0 = 0; // the cumul sum of all segment heights so far
	    
	    int lineHeight = getLineHeight(g);

	    for (int linenum=0; linenum < paragraphs.size(); linenum++) {
	    	String line = paragraphs.get(linenum);
	    	int absoluteY = (linenum+1) * lineHeight;
	    	int relativeY = absoluteY - absoluteHeightAtCurrentY0;
	    	if (relativeY >= segmentPixelHeight) {  // TODO extra buffering space
	    		// store current segment and cleanup
	    		segmentImages.add(curimg);
	    		segmentHeights.add(usedHeightInCurrentSegment);
	    		g.dispose();
	    		// advance to new segment
	    		pair = createNewSegment(makeImage, width, segmentPixelHeight);
	    		g = pair.second;
	    		curimg = pair.first;
	    		absoluteHeightAtCurrentY0 += usedHeightInCurrentSegment;
	    		usedHeightInCurrentSegment = 0;
	    		relativeY = absoluteY - absoluteHeightAtCurrentY0;
	    	}
	    	assert relativeY < segmentPixelHeight;
	    	
			g.drawString(line, 0, relativeY);
			usedHeightInCurrentSegment = relativeY;
			linenum++;
		}
	    
	    // finish the current segment -- copy and pasted from the loop, sigh
		segmentImages.add(curimg);
		segmentHeights.add(usedHeightInCurrentSegment);
		g.dispose();
		
		// vertical concatenation
		int totalHeight = segmentHeights.stream().mapToInt(x->x).sum();
		Image finalImage = makeImage.apply(totalHeight);
		g = (Graphics2D) finalImage.getGraphics();
		int absY=0;
		for (int seg=0; seg<segmentImages.size(); seg++) {
			g.drawImage(segmentImages.get(seg), 0, absY, null);
//			g.drawLine(0,absY,width,absY);
			absY += segmentHeights.get(seg);
		}
		assert absY==totalHeight;
		g.dispose();
		r.image = finalImage;
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
		
		JFrame main = new JFrame() {{ setSize(new Dimension(200,200)); }};
		MyTextArea a = new MyTextArea();
		a.doc = d;
		a.loadDocumentIntoRenderingDatastructures();
		main.add(a.top());
		main.setVisible(true);
	}
}

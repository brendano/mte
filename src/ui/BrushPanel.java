package ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.*;

import d.Document;

import util.U;

/** selector thingy */
class Brush {
	int x1=-1, y1=-1;
	int x2=-1, y2=-1;
	
	// info for drag movement
	Point initialMousePosition;
	int initx1=-1,initx2=-1,inity1=-1,inity2=-1;
	
	Brush(int x, int y) {
		x1=x2=x;
		y1=y2=y;
	}
	
	Rectangle getRegion() {
		return new Rectangle(Math.min(x1,x2),Math.min(y1,y2), 
				Math.abs(x2-x1), Math.abs(y2-y1));
	}
	
	void storeCurrentPositionAsInitial() {
		initx1=x1; initx2=x2; inity1=y1; inity2=y2; 
	}
	
}

@SuppressWarnings("serial")
public class BrushPanel extends JPanel implements MouseListener, MouseMotionListener {
	
	// (meta)data value space
	double minUserX=1984;
	double maxUserX=2015;
	double minUserY=-2;
	double maxUserY=2;
	
	// swing coordinate space
	double minPhysX = -1;
	double minPhysY = -1;
	double maxPhysX = -1;
	double maxPhysY = -1;
	
	int marLeft = 40;
	int marBottom = 50;
	int marTop = 20;
	int marRight = 20;
	int tickSize = 5;
	
	Brush brush = null;
	List<MyPoint> points;
	QueryReceiver queryReceiver;

	class MyPoint {
		Document doc;
		boolean isSelected;
	
		public int physX() {
			return (int) x_u2p(doc.x);
		}
		public int physY() {
			return (int) y_u2p(doc.y);
		}
		public Point physPoint() {
			return new Point(physX(), physY());
		}
	}

	public void setMySize(int w, int h) {
        setPreferredSize(new Dimension(w,h));
        // tricky: y=0 is top, y=high is bottom
        minPhysX = marLeft;
        minPhysY = marTop;
        maxPhysX = w-marRight;
        maxPhysY = h-marBottom;
	}

	/** user coordinates from physical (UI library) coordinates */
	double x_p2u(double physX) {
		double uscale = maxUserX-minUserX;
		double pscale = maxPhysX-minPhysX;
		double relpos = (physX-minPhysX)/pscale;
		return (relpos*uscale) + minUserX;
	}
	double y_p2u(double physY) {
		double uscale = maxUserY-minUserY;
		double pscale = maxPhysY-minPhysY;
		double relpos = (physY-minPhysY)/pscale;
		return (relpos*uscale) + minUserY;
	}
	double x_u2p(double userX) {
		double uscale = maxUserX-minUserX;
		double pscale = maxPhysX-minPhysX;
		double relpos = (userX-minUserX)/uscale;
		return (relpos*pscale) + minPhysX;
	}
	double y_u2p(double userY) {
		double uscale = maxUserY-minUserY;
		double pscale = maxPhysY-minPhysY;
		double relpos = (userY-minUserY)/uscale;
//		double o = maxPhysY - (relpos*pscale);
//		U.pf("uy=%s -> py=%s\n", userY, o);
		return maxPhysY - (relpos*pscale);
	}

	Mode mode = Mode.NO_BRUSH;
	
	static enum Mode {
		NO_BRUSH, 
		DRAWING_BRUSH,  // you're starting the drag to draw the brush. 
		STILL_BRUSH,  // you're done drawing (mouse released) and the brush is being held in place
		MOVING_BRUSH; // you're dragging the brush-box around.
	}
	
	boolean isDuringBrush() {
		return mode==Mode.DRAWING_BRUSH;
	}
	
	List<Integer> selectPoints(Rectangle q) {
		List<Integer> ret = new ArrayList<>();
		for (int i=0; i<points.size(); i++) {
			if (q.contains(points.get(i).physPoint())) {
				ret.add(i);
			}
		}
		return ret;
	}
	
	public BrushPanel(QueryReceiver qr, Collection<Document> docs) {
		super();
		addMouseListener(this);
		addMouseMotionListener(this);
		
		queryReceiver = qr;
		
		points = new ArrayList<>();
		for (Document d : docs) {
			MyPoint p = new MyPoint();
			p.doc = d;
			p.isSelected = false;
			points.add(p);
		}
		
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {
	}

	@Override
	public void mousePressed(MouseEvent e) {
		if (mode==Mode.STILL_BRUSH && !brush.getRegion().contains(e.getPoint())) {
			clearSelection();
			setMode(Mode.NO_BRUSH);
			brush = null;
			repaint();
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) {
	}

	@Override
	public void mouseExited(MouseEvent e) {
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (mode==Mode.DRAWING_BRUSH) {
			setMode(Mode.STILL_BRUSH);
		}
		else if (mode==Mode.MOVING_BRUSH) {
			setMode(Mode.STILL_BRUSH);
			brush.initialMousePosition = null;
		}
	}
	void setMode(Mode newMode) {
//		U.p("newmode " + newMode);
		mode = newMode;
	}

	@Override
	public void mouseDragged(MouseEvent e) {
//		U.p("mousedrag at current mode " + mode);
		if (mode==Mode.NO_BRUSH) {
			// Start a brush
			brush = new Brush(e.getX(), e.getY());
			clearSelection();
			setMode(Mode.DRAWING_BRUSH);
		}
		else if (mode==Mode.DRAWING_BRUSH) {
			// keep drawing
			brush.x2=e.getX(); brush.y2=e.getY();
			refreshSelection();
		}
		else if (mode==Mode.STILL_BRUSH && brush.getRegion().contains(e.getPoint())) {
			// start a move
			brush.initialMousePosition = e.getPoint();
			brush.storeCurrentPositionAsInitial();
			setMode(Mode.MOVING_BRUSH);
			continueBrushMove(e);
			refreshSelection();
		}
		else if (mode==Mode.MOVING_BRUSH) {
			continueBrushMove(e);
			refreshSelection();
		}
		repaint();
	}
	
	void refreshSelection() {
		clearSelection();
		for (int i : selectPoints(brush.getRegion())) {
			points.get(i).isSelected=true;
		}
		queryReceiver.receiveQuery(getSelectedDocIds());
	}

	void continueBrushMove(MouseEvent e) {
		int dx = e.getX() - brush.initialMousePosition.x;
		int dy = e.getY() - brush.initialMousePosition.y;
		brush.x1 = brush.initx1 + dx;
		brush.x2 = brush.initx2 + dx;
		brush.y1 = brush.inity1 + dy;
		brush.y2 = brush.inity2 + dy;
	}
	
	
	List<String> getSelectedDocIds() {
		List<String> ret = new ArrayList<>();
		for (MyPoint p : points) {
			if (p.isSelected) {
				ret.add( p.doc.docid );
			}
		}
		return ret;
	}
	void clearSelection() {
		for (MyPoint p : points) p.isSelected=false;
	}
	
	public void paintComponent(Graphics _g) {
		Graphics2D g = (Graphics2D)_g;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setBackground(Color.white);
		super.paintComponent(_g);
		
		drawAxes(g);

		for (int i=0; i<points.size(); i++) {
			Color c = points.get(i).isSelected ? Color.blue : Color.black;
			g.setColor(c);
			Point p = points.get(i).physPoint();
			GUtil.drawCenteredFilledCircle(g, p.x, p.y, 6);
		}
		renderBrush(g);
	}
	
	void drawAxes(Graphics2D g) {
		drawRect(g, minPhysX, minPhysY, maxPhysX-minPhysX, maxPhysY-minPhysY);
		for (double x=xtickMin(); x<=xtickMax(); x+=xtickDelta()) {
			g.drawLine((int)x_u2p(x), (int)(maxPhysY+tickSize), (int)x_u2p(x), (int)maxPhysY );
		}
		for (double x=xtickMin(); x<=xtickMax(); x+=xlabelDelta()) {
//			g.transform(AffineTransform.getTranslateInstance((double)getWidth(),0));
//			g.transform(AffineTransform.getRotateInstance(Math.PI/2));
			GUtil.drawCenteredString(g, renderXtick(x), (int) x_u2p(x), (int)(maxPhysY+2*tickSize), 0, 1);
//			g.transform(AffineTransform.getRotateInstance(-Math.PI/2));
//			g.transform(AffineTransform.getTranslateInstance(-(double)getWidth(),0));
		}
		for (double y=ytickMin(); y<=ytickMax(); y+=ytickDelta()) {
			g.drawLine((int)(minPhysX-tickSize), (int)y_u2p(y), (int)(minPhysX), (int)y_u2p(y));
			GUtil.drawCenteredString(g, renderYtick(y), (int)(minPhysX-tickSize*2), (int) y_u2p(y), -1, -0.3);
		}
	}

	String renderXtick(double ux) {
		return U.sf("%.0f",ux);
	}
	String renderYtick(double uy) {
		return U.sf("%.0f", uy);
	}
	double xtickMin() {
		return minUserX;
	}
	double xtickMax() {
		return maxUserX;
	}
	double xtickDelta() {
		return 1;
	}
	double xlabelDelta() {
		return 4;
	}
	double ytickMin() {
//		return minUserY;
		return -1;
	}
	double ytickMax() {
//		return maxUserY;
		return 1;
	}
	double ytickDelta() {
		return 1;
	}
	static void drawRect(Graphics2D g, double x, double y, double w, double h) {
		g.drawRect((int)x,(int)y,(int)w,(int)h);
	}

	void renderBrush(Graphics2D g) {
		if (mode==Mode.NO_BRUSH) return;
		assert brush != null;
		g.setColor(Color.gray);
		g.setStroke(new BasicStroke(3));
		Rectangle r = brush.getRegion();
    	g.drawRect(r.x, r.y, r.width, r.height);
	}

	@Override
	public void mouseMoved(MouseEvent e) {
	}

}

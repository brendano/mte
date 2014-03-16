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
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.swing.*;

import d.Document;

import util.U;

/** selector thingy */
class Brush {
	int startX=-1, startY=-1;
	int endX=-1, endY=-1;
	
	Brush(int x, int y) {
		startX=endX=x;
		startY=endY=y;
	}
	
	Rectangle getRegion() {
		return new Rectangle(Math.min(startX,endX),Math.min(startY,endY), 
				Math.abs(endX-startX), Math.abs(endY-startY));
	}
}

@SuppressWarnings("serial")
public class BrushPanel extends JPanel implements MouseListener, MouseMotionListener {
	
	// (meta)data value space
	double minUserX=1980;
	double maxUserX=2015;
	double minUserY=-1.5;
	double maxUserY=1.5;
	
	// swing coordinate space
	double minPhysX = 5;
	double minPhysY = 5;
	double maxPhysX = -1;
	double maxPhysY = -1;
	
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
        int mar = 5;
        minPhysX = mar;
        minPhysY = mar;
        maxPhysX = w-mar;
        maxPhysY = h-mar;
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

	boolean isDuringBrush() {
		return brush != null;
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
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		endBrush();
		repaint();
	}

	@Override
	public void mouseEntered(MouseEvent e) {
	}

	@Override
	public void mouseExited(MouseEvent e) {
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		if (!isDuringBrush()) {
			startBrush(e.getX(),e.getY());
		}
		else {
			continueBrush(e.getX(),e.getY());
		}
		repaint();
	}
	
	void startBrush(int x, int y) {
		brush = new Brush(x,y);
		clearSelection();
	}
	void continueBrush(int x, int y) {
		assert isDuringBrush();
		brush.endX=x; brush.endY=y;
		clearSelection();
		for (int i : selectPoints(brush.getRegion())) {
			points.get(i).isSelected=true;
		}
		queryReceiver.receiveQuery(getSelectedDocIds());
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
	void endBrush() {
		brush = null;
		clearSelection();
	}
	void clearSelection() {
		for (MyPoint p : points) p.isSelected=false;
	}
	
	public void paintComponent(Graphics _g) {
		Graphics2D g = (Graphics2D)_g;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setBackground(Color.white);
		super.paintComponent(_g);

		for (int i=0; i<points.size(); i++) {
			Color c = points.get(i).isSelected ? Color.blue : Color.black;
			g.setColor(c);
			Point p = points.get(i).physPoint();
			
			Ellipse2D.Double circle = new Ellipse2D.Double(p.x, p.y, 6,6);
			g.fill(circle);
		}
		renderBrush(g);
	}

	void renderBrush(Graphics2D g) {
		if (!isDuringBrush()) return;
		g.setColor(Color.gray);
		g.setStroke(new BasicStroke(3));
		Rectangle r = brush.getRegion();
    	g.drawRect(r.x, r.y, r.width, r.height);
	}

	@Override
	public void mouseMoved(MouseEvent e) {
	}

}

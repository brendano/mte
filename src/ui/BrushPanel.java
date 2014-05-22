package ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.*;

import d.Corpus;
import d.Document;
import d.Schema;
import d.Schema.Levels;
import d.Schema.Levels.Level;
import d.TermQuery;
import util.U;

import java.util.Comparator;

@SuppressWarnings("serial")
public class BrushPanel extends JPanel implements MouseListener, MouseMotionListener {
	
	String xattr, yattr;
	Schema schema;
	
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
	
	int marLeft = 100;
	int marBottom = 50;
	int marTop = 20;
	int marRight = 20;
	int tickSize = 5;
	
	Brush brush = null;
	List<MyPoint> points;
	Map<String,MyPoint> pointsByDocid;
	QueryReceiver queryReceiver;
	
	Color BRUSH_COLOR = new Color(61,56,240);
	
	class MyPoint {
		Document doc;
		boolean isDocquerySelected = false;
		boolean isTermquery1Selected = false;
		boolean isTermquery2Selected = false;
	
		public int physX() {
			return (int) x_u2p(schema.getDouble(doc, xattr));
		}
		public int physY() {
			return (int) y_u2p(schema.getDouble(doc, yattr));
		}
		public Point physPoint() {
			return new Point(physX(), physY());
		}
	}

	private void setPhysDimsToCurrentSize() {
		setMySize(getWidth(), getHeight());
	}
	private void setMySize(int w, int h) {
        // tricky: y=0 is top, y=high is bottom
        minPhysX = marLeft;
        minPhysY = marTop;
        maxPhysX = w-marRight;
        maxPhysY = h-marBottom;
//        U.pf("BrushPanel set to: xs %s %s || ys %s %s\n", minPhysX,maxPhysX, minPhysY, maxPhysY);
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
		pointsByDocid = new HashMap<>();
		for (Document d : docs) {
			MyPoint p = new MyPoint();
			p.doc = d;
			p.isDocquerySelected = false;
			points.add(p);
			pointsByDocid.put(d.docid, p);
		}

		addComponentListener(new ResizerHandler());
	}
	class ResizerHandler extends ComponentAdapter {
		public void componentResized(ComponentEvent e) {
			BrushPanel bp = ((BrushPanel) e.getComponent());
			bp.setPhysDimsToCurrentSize();
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
			queryReceiver.receiveQuery(new ArrayList<>());
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
			points.get(i).isDocquerySelected=true;
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
			if (p.isDocquerySelected) {
				ret.add( p.doc.docid );
			}
		}
		return ret;
	}
	void clearSelection() {
		for (MyPoint p : points) p.isDocquerySelected=false;
	}
	
	public void paintComponent(Graphics _g) {
		Graphics2D g = (Graphics2D)_g;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setBackground(Color.white);
		super.paintComponent(_g);
		
		drawAxes(g);
		
		Collections.sort(points, Comparator
				.comparingInt((MyPoint p) -> p.isTermquery1Selected ? 1:0)
				.thenComparingInt((MyPoint p) -> p.isDocquerySelected ? 1:0)
		);
		
		for (int i=0; i<points.size(); i++) {
			MyPoint mp = points.get(i);
			Color c = mp.isTermquery1Selected ? GUtil.Dark2[0] :
							mp.isDocquerySelected ? Color.black : 
							Color.gray;
			g.setColor(c);
			Point p = mp.physPoint();
			if (mp.isTermquery1Selected) {
				GUtil.drawCenteredTriangle(g, p.x, p.y, 6, mp.isTermquery1Selected);
			}
			else {
				GUtil.drawCenteredCircle(g, p.x, p.y, 6, mp.isTermquery1Selected);
			}
			
		}
		renderBrush(g);
	}
	
	public void showTerms(TermQuery tq) {
		for (MyPoint p : points) {
			p.isTermquery1Selected = false;
		}
		for (Document d : tq.getMatchingDocs().docs()) {
			pointsByDocid.get(d.docid).isTermquery1Selected = true;
		}
		repaint();
	}


	
	double largerTickMultiplier = 1.6;
	double tickLabelOffset = 2.0;
	
	void drawAxes(Graphics2D g) {
		double aa = tickLabelOffset;
		double bb = largerTickMultiplier;
		drawRect(g, minPhysX, minPhysY, maxPhysX-minPhysX, maxPhysY-minPhysY);
		for (double x=xtickMin(); x<=xtickMax(); x+=xtickDelta()) {
			g.drawLine((int)x_u2p(x), (int)(maxPhysY+tickSize), (int)x_u2p(x), (int)maxPhysY );
		}
		for (double x : xtickPositions()) {
			GUtil.drawCenteredString(g, renderXtick(x), x_u2p(x), maxPhysY+aa*tickSize, 0, 1);
			GUtil.drawLine(g, x_u2p(x), maxPhysY+bb*tickSize, x_u2p(x), maxPhysY);
		}
		for (double y : ytickPositions()) {
			GUtil.drawLine(g, minPhysX-tickSize, y_u2p(y), minPhysX, y_u2p(y));
			GUtil.drawCenteredString(g, renderYtick(y), minPhysX-aa*tickSize, y_u2p(y), -1, -0.3);
		}
	}
	
	List<Double> xtickPositions() {
		List<Double> ret = new ArrayList<>();
		for (double x=xtickMin(); x<=xtickMax(); x+=xlabelDelta()) {
			ret.add(x);
		}
		return ret;
	}
	List<Double> ytickPositions() {
		List<Double> ret = new ArrayList<>();
		if (schema.column(yattr).isCateg()) {
			for (Level lev : schema.column(yattr).levels.levels()) {
				ret.add( (double) lev.number);
			}
		}
		else {
			for (double y=ytickMin(); y<=ytickMax(); y+=ytickDelta()) {
				ret.add(y);
			}
		}
		return ret;
	}
	
	double scaleMult = 0.1;
	
	double x(Document d) {
		return schema.getDouble(d, xattr);
	}
	double y(Document d) {
		return schema.getDouble(d, yattr);
	}
	public void setDefaultXYLim(Corpus corpus) {
		double xmin=Double.POSITIVE_INFINITY,xmax=Double.NEGATIVE_INFINITY;
		double ymin=Double.POSITIVE_INFINITY,ymax=Double.NEGATIVE_INFINITY;
		for (Document d : corpus.allDocs()) {
			if (x(d) < xmin) xmin=x(d);
			if (x(d) > xmax) xmax=x(d);
			if (y(d) < ymin) ymin=y(d);
			if (y(d) > ymax) ymax=y(d);
		}
		double scale;
		scale = ymax-ymin;
		minUserY = ymin - scale*scaleMult;
		maxUserY = ymax + scale*scaleMult;
		scale = xmax-xmin;
		minUserX = xmin - scale*scaleMult;
		maxUserX = xmax + scale*scaleMult;
	}

	String renderXtick(double ux) {
		return U.sf("%.0f",ux);
	}
	boolean isIntegral(double x) {
		int rounded = (int) Math.round(x);
		return Math.abs(rounded-x) < 1e-100;
	}
	String renderYtick(double uy) {
		if (schema.column(yattr).isCateg() && isIntegral(uy)) {
			int i = (int) Math.round(uy);
			if (schema.column(yattr).levels.num2level.containsKey(i)) {
				return schema.column(yattr).levels.num2level.get(i).displayName();
			}
		}
		return U.sf("%.0f", uy);	
	}
	double xtickMin() {
		return minUserX;
	}
	double xtickMax() {
		return maxUserX;
	}
	double xtickDelta() {
		double range = xtickMax() - xtickMin();
		double d1 = range/32;
		return Math.round(d1);
//		return 1;
	}
	double xlabelDelta() {
		double range = xtickMax() - xtickMin();
		double d1 = range/8;
		return Math.round(d1);
	}
	double ytickMin() {
		return minUserY;
//		return roundup(minUserY, ytickDelta());
	}
	double ytickMax() {
		return maxUserY;
//		return rounddown(maxUserY, ytickDelta());
	}
	double ytickDelta() {
		double range = ytickMax() - ytickMin();
		return Math.round(range/8);
	}
	double roundup(double x, double granularity) {
		return Math.ceil(x/granularity)*granularity;
	}
	double rounddown(double x, double granularity) {
		return Math.floor(x/granularity)*granularity;
	}
	static void drawRect(Graphics2D g, double x, double y, double w, double h) {
		g.drawRect((int)x,(int)y,(int)w,(int)h);
	}

	void renderBrush(Graphics2D g) {
		if (mode==Mode.NO_BRUSH) return;
		assert brush != null;
		g.setColor(BRUSH_COLOR);
		g.setStroke(new BasicStroke(3));
		Rectangle r = brush.getRegion();
    	g.drawRect(r.x, r.y, r.width, r.height);
	}

	@Override
	public void mouseMoved(MouseEvent e) {
	}

}


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


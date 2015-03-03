package te.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import com.google.common.eventbus.Subscribe;

import te.data.Corpus;
import te.data.Document;
import te.data.Schema;
import te.data.TermQuery;
import te.ui.queries.AllQueries;
import util.U;

/**
 * ISSUE: with the allqueries refactor, its data structures are updated fairly realtime.
 * would be better to have a more transaction/commit sort of process where a big changeset is atomically applied all at once.
 * it's either that, or a message approach.  the old system was a message approach, but that was starting to get messy.
 */
public class BrushPanel extends JPanel implements MouseListener, MouseMotionListener {
	
	String xattr, yattr;  // allowed to be NULL.
	Schema schema;
	
	// "user": (meta)data value space
	double minUserX=1984;
	double maxUserX=2015;
	double minUserY=-2;
	double maxUserY=2;
	
	// "phys": graphics(~pixels) coordinate space, boundaries of the plotting area.
	// we maintain min<=max always, so the directionality of the y-axis is flipped compared to user space.
	double minPhysX = -1;
	double minPhysY = -1;
	double maxPhysX = -1;
	double maxPhysY = -1;
	
	int marLeft = 100;
	int marBottom = 50;
	int marTop = 20;
	int marRight = 20;
	int tickSize = 5;

	double largerTickMultiplier = 1.6;
	double tickLabelOffset = 2.0;
	
	Brush brush = null;
	List<MyPoint> points = new ArrayList<>();
	Map<String,MyPoint> pointsByDocid = new HashMap<>();
	DocSelectionListener queryReceiver;
	
	Color BRUSH_COLOR = new Color(61,56,240);
	
	class MyPoint {
		Document doc;
		boolean isDocquerySelected() {
			return AllQueries.instance().brushPanelCovariateSelectedDocIDs.contains(doc.docid);
//			return docSelection.contains(doc.docid); 
		}
		boolean isTermquery1Selected() {
			return AllQueries.instance().termQuery().getMatchingDocs().docsById.containsKey(doc.docid);
//			return termquerySelectedPointDocIDs.contains(doc.docid);
		}
		boolean isFulldocSelected() {
			String d = AllQueries.instance().fulldocPanelCurrentDocID;
			return d!=null && d==doc.docid;
//			return this==fulldocSelectedPoint; 
		}
	
		public double physX() {
			return x_u2p(xOfDoc(doc));
		}
		public double physY() {
			return y_u2p(yOfDoc(doc));
		}
		public Point2D.Double physPoint() {
			return new Point2D.Double(physX(), physY());
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

	/* Conversion between user and physical coordinate systems */
	double uscaleX() {
		return maxUserX-minUserX;
	}
	double pscaleX() {
		return maxPhysX-minPhysX;
	}
	double uscaleY() {
		return maxUserY-minUserY;
	}
	double pscaleY() {
		return maxPhysY-minPhysY;
	}
	double x_p2u(double physX) {
		double relpos = (physX-minPhysX)/pscaleX();
		return (relpos*uscaleX()) + minUserX;
	}
	double x_u2p(double userX) {
		double relpos = (userX-minUserX)/uscaleX();
		return (relpos*pscaleX()) + minPhysX;
	}
	double y_p2u(double physY) {
		double relpos = (physY-minPhysY)/pscaleY();
		return maxUserY - (relpos*uscaleY());
	}
	double y_u2p(double userY) {
		double relpos = (userY-minUserY)/uscaleY();
		return maxPhysY - (relpos*pscaleY());
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
	
	public BrushPanel(DocSelectionListener qr, Collection<Document> docs) {
		super();
        setOpaque(true);
        setBackground(Color.white);
        setBorder(BorderFactory.createLineBorder(Color.black));
		addMouseListener(this);
		addMouseMotionListener(this);
		addComponentListener(new ResizerHandler());
		queryReceiver = qr;
		for (Document d : docs) {
			MyPoint p = new MyPoint();
			p.doc = d;
			points.add(p);
			pointsByDocid.put(d.docid, p);
		}
	}
	
	class ResizerHandler extends ComponentAdapter {
		public void componentResized(ComponentEvent e) {
			BrushPanel bp = ((BrushPanel) e.getComponent());
			bp.setPhysDimsToCurrentSize();
			bp.repaint();
		}
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {
	}

	@Override
	public void mouseEntered(MouseEvent e) {
	}

	@Override
	public void mouseExited(MouseEvent e) {
	}

	@Override
	public void mousePressed(MouseEvent e) {
		if (mode==Mode.STILL_BRUSH && !brush.getRegionPhys().contains(e.getPoint())) {
			clearSelection();
			setMode(Mode.NO_BRUSH);
			brush = null;
			repaint();
			queryReceiver.receiveDocSelection(new ArrayList<>());
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		if (mode==Mode.DRAWING_BRUSH) {
			setMode(Mode.STILL_BRUSH);
		}
		else if (mode==Mode.MOVING_BRUSH) {
			setMode(Mode.STILL_BRUSH);
			brush.initialMousePositionX = null;
			brush.initialMousePositionY = null;
		}
	}
	void setMode(Mode newMode) {
//		U.p("newmode " + newMode);
		mode = newMode;
	}

	@Override
	public void mouseDragged(MouseEvent e) {
//		U.p("mousedrag at current mode " + mode);
		// TODO this is where to only analyze the diff from the previous brush position for a faster selected docset update.
		if (mode==Mode.NO_BRUSH) {
			// Start a brush
			brush = new Brush(x_p2u(e.getX()), y_p2u(e.getY()));
//			brush.storeCurrentPositionAsInitial();
			clearSelection();
			setMode(Mode.DRAWING_BRUSH);
		}
		else if (mode==Mode.DRAWING_BRUSH) {
			// keep drawing
			brush.x2=x_p2u(e.getX());
			brush.y2=y_p2u(e.getY());
			refreshSelection();
		}
		else if (mode==Mode.STILL_BRUSH && brush.getRegionPhys().contains(e.getPoint())) {
			// start a move
			brush.initialMousePositionX = x_p2u(e.getX());
			brush.initialMousePositionY = y_p2u(e.getY());
			brush.storeCurrentPositionAsInitial();
			setMode(Mode.MOVING_BRUSH);
			continueBrushMove(e);
			refreshSelection();
		}
		else if (mode==Mode.MOVING_BRUSH) {
			continueBrushMove(e);
			refreshSelection();
		}
//		U.p("DRAG "+brush);
		repaint();
	}
	
	void continueBrushMove(MouseEvent e) {
		double dx = (x_p2u(e.getX()) - brush.initialMousePositionX);
		double dy = (y_p2u(e.getY()) - brush.initialMousePositionY);
		brush.x1 = brush.initx1 + dx;
		brush.x2 = brush.initx2 + dx;
		brush.y1 = brush.inity1 + dy;
		brush.y2 = brush.inity2 + dy;
	}
	
	void refreshSelection() {
		clearSelection();
		Set<String> docsel = AllQueries.instance().brushPanelCovariateSelectedDocIDs;
		for (int i : selectPoints(brush.getRegionPhys())) {
			docsel.add(points.get(i).doc.docid);
		}
		repaint();
	}

	void clearSelection() {
		AllQueries.instance().brushPanelCovariateSelectedDocIDs.clear();
	}
	
	public void paintComponent(Graphics _g) {
		Graphics2D g = (Graphics2D)_g;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setBackground(Color.white);
		super.paintComponent(_g);
		
		drawAxes(g);
		
		Collections.sort(points, Comparator
				.comparingInt((MyPoint p) -> p.isTermquery1Selected() ? 1:0)
				.thenComparingInt((MyPoint p) -> p.isDocquerySelected() ? 1:0)
		);
		
		for (int i=0; i<points.size(); i++) {
			MyPoint mp = points.get(i);
			Color c = mp.isTermquery1Selected() ? GUtil.Dark2[0] :
							mp.isDocquerySelected() ? Color.black : 
							Color.gray;
			g.setColor(c);
			Point2D.Double p = mp.physPoint();
			if (mp.isTermquery1Selected()) {
				GUtil.drawCenteredTriangle(g, p.x, p.y, 3, mp.isTermquery1Selected());
			}
			else {
				GUtil.drawCenteredCircle(g, p.x, p.y, 3, mp.isTermquery1Selected());
			}
			g.setColor(Color.black);
			if (mp.isFulldocSelected()) {
				GUtil.drawCenteredCircle(g, p.x, p.y, 4, false);
			}
		}
		renderBrush(g);
	}
	
	
	void drawAxes(Graphics2D g) {
		double aa = tickLabelOffset;
		double bb = largerTickMultiplier;
		drawRect(g, minPhysX, minPhysY, maxPhysX-minPhysX, maxPhysY-minPhysY);
//		for (double x=xtickMin(); x<=xtickMax(); x+=xtickDelta()) {
//			g.drawLine((int)x_u2p(x), (int)(maxPhysY+tickSize), (int)x_u2p(x), (int)maxPhysY );
//		}
		for (double x : xtickPositions()) {
			GUtil.drawLine(g, x_u2p(x), maxPhysY+bb*tickSize, x_u2p(x), maxPhysY);
			GUtil.drawCenteredString(g, renderXtick(x), x_u2p(x), maxPhysY+aa*tickSize, 0, 1);
		}
		for (double y : ytickPositions()) {
			GUtil.drawLine(g, minPhysX-tickSize, y_u2p(y), minPhysX, y_u2p(y));
			GUtil.drawCenteredString(g, renderYtick(y), minPhysX-aa*tickSize, y_u2p(y), -1, -0.3);
		}
	}
	
	enum XorY { X, Y }
	
	List<Double> tickPositions(String attr, XorY x_or_y) {
		if (attr==null) {
			return Collections.emptyList();
		}
		else if (schema.column(attr).isCateg()) {
			return schema.column(attr).levels.levels().stream().
					map((lev) -> (double) lev.number).
					collect(Collectors.toList());
		}
		else {
			double min=-42,max=-42,delta=-42;
			if (x_or_y==XorY.X) {
				min=xtickMin(); max=xtickMax(); delta=xlabelDelta();
			}
			else if (x_or_y==XorY.Y) {
				min=ytickMin(); max=ytickMax(); delta=ytickDelta();
			}
//			U.pf("min,max,delta = %s %s %s\n", min,max,delta);
			List<Double> ret = new ArrayList<>();
			for (double x=min; x<=max; x+=delta) {
				ret.add(x);
			}
			return ret;
		}
	}
	
	List<Double> xtickPositions() {
		return tickPositions(xattr, XorY.X);
	}
	List<Double> ytickPositions() {
		return tickPositions(yattr, XorY.Y);
	}
	
	double scaleMult = 0.1;
	
	double xOfDoc(Document d) {
		if (xattr==null) return 0;
		return schema.getDouble(d, xattr);
	}
	double yOfDoc(Document d) {
		if (yattr==null) return 0;
		return schema.getDouble(d, yattr);
	}
	class Range { double min, max;  double scale() { return max-min; }}
	
	Range getDataRange(Corpus corpus, String attr) {
		Range r = new Range();
		if (attr==null) {
			r.min=-1; r.max=1;
		} else {
			r.min = corpus.covariateSummaries.get(attr).min();
			r.max = corpus.covariateSummaries.get(attr).max();
		}
		return r;
	}
	
	public void setDefaultXYLim(Corpus corpus) {
		Range xr = getDataRange(corpus, xattr);
		Range yr = getDataRange(corpus, yattr);
		double scale;
		scale = yr.scale();
		scale = scale==0 ? 1 : scale;
		minUserY = yr.min - scale*scaleMult;
		maxUserY = yr.max + scale*scaleMult;
		scale = xr.scale();
		scale = scale==0 ? 1: scale;
		minUserX = xr.min - scale*scaleMult;
		maxUserX = xr.max + scale*scaleMult;
	}

	boolean isIntegral(double x) {
		int rounded = (int) Math.round(x);
		return Math.abs(rounded-x) < 1e-100;
	}
	String renderXtick(double ux) {
		return tickText(xattr, ux, XorY.X);
	}
	String renderYtick(double uy) {
		return tickText(yattr, uy, XorY.Y);
	}
	String tickText(String attr, double userspaceValue, XorY which) {
		if (schema.column(attr).isCateg()) {
			assert isIntegral(userspaceValue) : "this needs to be rounded for categ variable: " + userspaceValue;
			int i = (int) Math.round(userspaceValue);
			assert schema.column(attr).levels.num2level.containsKey(i) : "weird rounding issue for categ variable: " + userspaceValue;
			return schema.column(attr).levels.num2level.get(i).displayName();
		}
		else {
			return reasonableNumericRounding(attr, userspaceValue, which);
		}
	}
	
	String reasonableNumericRounding(String attr, double userspaceValue, XorY which) {
		double range = which==XorY.X ? maxUserX-minUserX : maxUserY-minUserY;
		if (range==0) range=1;
		if (range >= 10) {
			return U.sf("%.0f", userspaceValue);
		} else {
			double log10 = Math.log10(range);
			int numDecimalPointsNeeded = (int) (Math.abs(Math.floor(log10)) + 2); // should be only +1 or so if fix tickmarks to be at friendly rounding points
			if (numDecimalPointsNeeded < 6) {
				String fmt = "%." + numDecimalPointsNeeded + "f";
				return U.sf(fmt, userspaceValue);
			}
			else {
				return U.sf("%.3e", userspaceValue);
			}
		}
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
		return niceTickAmount(d1);
	}
	double ytickDelta() {
		double range = ytickMax() - ytickMin();
		return niceTickAmount(range/8);
	}
	double xlabelDelta() {
		double range = xtickMax() - xtickMin();
		return niceTickAmount(range/6);
	}

	double niceTickAmount(double ticksize) {
		if (Math.abs(ticksize) > 1) {
			return Math.round(ticksize);	
		}
		else {
			return ticksize;
		}
	}
	double ytickMin() {
		return minUserY;
//		return roundup(minUserY, ytickDelta());
	}
	double ytickMax() {
		return maxUserY;
//		return rounddown(maxUserY, ytickDelta());
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
		Rectangle r = brush.getRegionPhys();
//		U.p("RENDER " + r);
    	g.drawRect(r.x,r.y,r.width,r.height);
	}

	@Override
	public void mouseMoved(MouseEvent e) {
	}

	
	/** selector thingy. coordinates are stored in user space */
	class Brush {
		double
				x1=Double.NEGATIVE_INFINITY, y1=Double.NEGATIVE_INFINITY,
				x2=Double.POSITIVE_INFINITY, y2=Double.POSITIVE_INFINITY;
		
		// info for drag movement
		Double initialMousePositionX;
		Double initialMousePositionY;
		double initx1=x1,initx2=x2,inity1=y1,inity2=y2;
		
		Brush(double x, double y) {
			x1=x2=x;
			y1=y2=y;
		}
		
		Rectangle getRegionPhys() {
			int px1 = (int) x_u2p(Math.min(x1,x2));
			int py1 = (int) y_u2p(Math.max(y1,y2)); // y-axis flip
			int px2 = (int) x_u2p(Math.max(x1,x2));
			int py2 = (int) y_u2p(Math.min(y1,y2)); // y-axis flip
			return new Rectangle(px1,py1, px2-px1, py2-py1);
		}
		
		void storeCurrentPositionAsInitial() {
			initx1=x1; initx2=x2; inity1=y1; inity2=y2; 
		}
		
		public String toString() { return String.format(
				"Brush[cur=(%.2f %.2f) (%.2f %.2f)  init=(%.2f %.2f) (%.2f %.2f)  initmousepos=(%s %s)",
				x1,y1,x2,y2, initx1,inity1, initx2,inity2, initialMousePositionX, initialMousePositionY);
		}
	}

}



package d;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.*;

import ui.GUtil;
import util.U;

public class SpatialIndex {
	int bottomReso;
	double xmin,xmax,ymin,ymax;
	String xAttr, yAttr;
	Cell root;
	
	public SpatialIndex(int n, double xmin, double xmax, double ymin, double ymax) {
		this.xmin=xmin; this.xmax=xmax; this.ymin=ymin; this.ymax=ymax;
		this.bottomReso = n;
		initializeTree();
	}
	
	void initializeTree() {
		root = new Cell();
		root.children = new Cell[bottomReso][bottomReso];
		for (int i=0;i<root.children.length;i++) {
			for (int j=0;j<root.children[i].length;j++) {
				root.children[i][j] = new Cell();
			}
		}
	}
	public void doSpatialSums(Collection<Document> docs) {
		for (Document d : docs) {
//			Cell c = root.children[celly(d.getDouble(yAttr))][cellx(d.getDouble(xAttr))];
			Cell c = null;  assert false : "BROKEN HERE";
			c.terms.addInPlace(d.termVec);
			c.docs.add(d);
		}
	}
	boolean isGoodCellX(int cx) {
		return cx>=0 && cx < bottomReso;
	}
	boolean isGoodCellY(int cy) {
		return cy>=0 && cy<bottomReso;
	}
	public TermVector calcSum(double qx1, double qx2, double qy1, double qy2) {
		TermVector result = new TermVector();
		double xscale=xmax-xmin, yscale=ymax-ymin;
		// these are supposed to be closed intervals over the integral cell space
		// TODO bug if exactly integral on a boundaryline
		int cx1 = (int) Math.ceil((qx1-xmin)/xscale) * bottomReso;
		int cx2 = (int) Math.floor((qx2-xmin)/xscale) * bottomReso - 1;
		int cy1 = (int) Math.ceil((qy1-ymin)/yscale) * bottomReso;
		int cy2 = (int) Math.floor((qy2-ymin)/yscale) * bottomReso - 1;
		
		// fast sums
		for (int j=cx1; j<=cx2; j++) {
			for (int i=cy1; i<=cy2; i++) {
				result.addInPlace(root.children[i][j].terms);
			}
		}
		
		// individual documents for the remainder
		int rx1 = cx1-1, rx2=cx2+1, ry1=cy1-1, ry2=cy2+1;

		for (Point cellpoint : rectangleRing(rx1,rx2, ry1,ry2)) {
			if (!isGoodCellX(cellpoint.x)) continue;
			if (!isGoodCellY(cellpoint.y)) continue;
			for (Document d : root.children[cellpoint.y][cellpoint.x].docs) {
				assert false : "BROKEN HERE";
//				if (contains(d.getDouble(xAttr), d.getDouble(yAttr),  qx1,qx2, qy1,qy2)) {
//					result.addInPlace(d.termVec);
//				}
			}
		}
		return result;
	}
	static boolean contains(double px,double py,  double qx1,double qx2, double qy1, double qy2) {
		return qx1 <= px && px <= qx2 && qy1 <= py && py <= qy2; 
	}
	/** [inclusive,inclusive] */
	static Collection< Point > rectangleRing(int x1,int x2,int y1,int y2) {
		List<Point> ret = new ArrayList<>();
		for (int x=x1; x<=x2; x++) {
			ret.add(new Point(x,y1));
			ret.add(new Point(x,y2));
		}
		for (int y=y1; y<=y2; y++) {
			ret.add(new Point(x1,y));
			ret.add(new Point(x2,y));
		}
		return ret;
	}
	public void dump() {
		for (int i=0; i<bottomReso; i++) {
			for (int j=0; j<bottomReso; j++) {
				U.pf("%d %d || %s\n", i,j, root.children[i][j].terms.map);
			}
		}
	}
	
	//////////////////
	
	double relx(double x) {
		return (x-xmin) / (xmax-xmin);
	}
	double rely(double y) {
		return (y-ymin) / (ymax-ymin);
	}
	int cellx(double x) {
		double rx = relx(x);
		if (rx==1) return bottomReso-1;
		return (int)(rx*bottomReso);
	}
	int celly(double y) {
		double ry = rely(y);
		if (ry==1) return bottomReso-1;
		return (int)(ry*bottomReso);
	}
	
}

class Cell {
	TermVector terms = new TermVector();
	Cell[][] children;
	List<Document> docs = new ArrayList<>();
}

class LevelGrid {
	
}
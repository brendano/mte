package ui;

import java.awt.Graphics2D;

import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import util.U;

public class GUtil {
	
    public static void drawLine(Graphics2D g, double x1, double y1, double x2, double y2) {
    	g.drawLine( (int) x1, (int) y1, (int) x2, (int) y2);
    }

	/** 
	 * centered: adjx,adjy = (0,0)
	 * right-aligned (flush left of the point): adjx=-1
	 * left-aligned (flush the right of the point): adjx=1
	 * top-aligned (flush below the point): adjy=1
	 * bottom-aligned (flush above the point): adjy=-1
	 * 
	 * i'm not sure that top/bottom alignment are working correctly
	 */
	public static void drawCenteredString(Graphics2D g, String s, double x, double y, double adjx, double adjy) {
        Rectangle2D r = g.getFontMetrics().getStringBounds(s, g);
        double finalx = x + r.getWidth() * (adjx-1)/2;
        double finaly = y + r.getHeight() * (adjy+1)/2;
        g.drawString(s, (float) finalx, (float) finaly); 
	}

	public static void drawCenteredCircle(Graphics2D g, int x, int y, int radius, boolean fill) {
		Ellipse2D.Double circle = new Ellipse2D.Double(x -radius/2, y -radius/2, radius, radius);
		if (fill) {
			g.fill(circle);
		} else {
			g.draw(circle);	
		}
	}
	static int roundInt(double x) {
		return (int) Math.round(x);
	}
	public static void drawCenteredTriangle(Graphics2D g, double x, double y, double radius, boolean fill) {
		// w,h are the half-width and half-height of the equilateral triangle, respectively
		// http://brenocon.com/Screen%20Shot%202014-03-19%20at%203.35.50%20PM.jpg
		double w = radius * Math.cos(Math.PI/6);
		double h = radius * Math.sin(Math.PI/6);
		double z = 0.8;  // horiz multiplier
		// botleft, topctr, botright
		int[] xs = new int[]{ roundInt(x-w*z), roundInt(x), roundInt(x+w*z) };
		int[] ys = new int[]{ roundInt(y+h), roundInt(y-h), roundInt(y+h) };
		if (fill) {
			g.fillPolygon(xs,ys,xs.length);
		} else {
			g.drawPolygon(xs,ys,xs.length);
		}
	}
	
	static boolean isInteger(double x) {
		return Math.abs(x - Math.round(x)) < 1e-100;
	}
	/** grid125(.001, 1) => [0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1.0] */
	public static List<Double> logGrid125(double min, double max) {
		assert min <= max;
		List<Double> ret = new ArrayList<>();
		double cur = min;
		while (cur <= max) {
			ret.add(cur);
			if (isInteger(Math.log10(Math.abs(cur)))) {
				cur *= 2;
			}
			else if (isInteger(Math.log10(Math.abs(cur/2)))) {
				double base = cur/2;
				cur = base*5;
			}
			else if (isInteger(Math.log10(Math.abs(cur/5)))) {
				cur *= 2;
			}
		}
		return ret;
	}
	
	public static List<Double> logGrid1s(double min, double max) {
		assert min <= max;
		List<Double> ret = new ArrayList<>();
		double curbase = Math.pow(10, Math.floor(Math.log10(min)));
		double curmult = min/curbase;
		U.p(curbase + " " + curmult);
		while ( (curbase*curmult) <= max) {
			ret.add( curbase*curmult );
			if (curmult==10) {
				curbase *= 10;
				curmult=0;
			}
			else {
				curmult++;	
			}
		}
		return ret;
	}
	
//	public static void main(String[] args) {
//		U.p(logGrid125( Double.parseDouble(args[0]), Double.parseDouble(args[1]) ));
//		U.p(logGrid1s( Double.parseDouble(args[0]), Double.parseDouble(args[1]) ));
//	}
	
	public static List<Integer> intRangeList(int start, int end) {
		assert start <= end;
		List<Integer> ret = new ArrayList<>();
		for (int i=start; i<end; i++) {
			ret.add(i);
		}
		return ret;
	}
}

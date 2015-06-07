package te.ui;

import java.text.ParseException;

import javax.swing.AbstractSpinnerModel;
import javax.swing.JFormattedTextField.AbstractFormatter;

import utility.util.U;

@SuppressWarnings("serial")
public class SpinnerStuff {
	public static void main(String[] args) {
		double x = Double.valueOf(args[0]);
		U.p(next_1_3(x));
		U.p(prev_1_3(x));
	}
	static double next_1_3(double x) {
		double curbase = Math.pow(10,Math.round(Math.log10(x)));
		double curdigit = Math.round(x/curbase);
		if (curdigit < 1)
			return 1.0*curbase;
		else if (curdigit >= 1 && curdigit < 3)
			return 3.0*curbase;
		else if (curdigit>=3 && curdigit<10)
			return 1.0*(curbase*10);
		else {
			U.p("weird");
			return 1.0*(curbase*10);
		}
	}
	static double prev_1_3(double x) {
		double curbase = Math.pow(10,Math.round(Math.log10(x)));
		double curdigit = Math.round(x/curbase);
		if (curdigit <= 1)
			return 3.0*curbase/10;
		else if (curdigit > 1 && curdigit <= 3)
			return 1.0*curbase;
		else if (curdigit>3 && curdigit<10)
			return 3.0*curbase;
		else {
			U.pf("weird %s %s\n", curdigit, curbase);
			return 1.0*(curbase/10);
		}
	}

	static class BaseMult {
		double base;
		int mult;
		BaseMult(double b, int m) { base=b; mult=m; }
		static BaseMult fromDouble(double x) {
			double b = Math.pow(10, Math.round(Math.log10(x)));
			int m = (int) Math.round(x/b);
			return new BaseMult(b,m);
		}
	}

	static class MySpinnerModel extends AbstractSpinnerModel {
		double prob;
		
		@Override
		public Object getValue() {
			return prob;
		}
	
		static double bounded(double p) {
			return GUtil.bounded(p, 1e-9, 1);
		}
		@Override
		public void setValue(Object value) {
			prob = (double) value;
			prob = roundToMyDisplayUnits(prob);
			prob = bounded(prob);
			fireStateChanged();
		}
	
		@Override
		public Object getNextValue() {
			return roundToMyDisplayUnits(bounded(next_1_3(prob)));
		}
		@Override
		public Object getPreviousValue() {
			return roundToMyDisplayUnits(bounded(prev_1_3(prob)));
		}
	}

	static class NiceFractionFormatter extends AbstractFormatter {
		@Override
		public Object stringToValue(String text) throws ParseException {
			String[] parts = text.split(" ");
			int mult = Integer.parseInt(parts[0].replace(",",""));
			int baseReciprocal = Integer.parseInt(parts[ parts.length-1 ].replace(",",""));
			double base = 1.0 / baseReciprocal;
			return mult*base;
		}
		@Override
		public String valueToString(Object value) throws ParseException {
			Double x = (Double) value;
			BaseMult bm = BaseMult.fromDouble(x);
			return U.sf("%d out of %s", bm.mult, GUtil.commaize((int) Math.round(1/bm.base)));
		}
	}
	
	static double roundToMyDisplayUnits(double x) {
		return Double.parseDouble(smartSigDigFormatter(x));
	}
	static String smartSigDigFormatter(double x) {
		double closestInteger = Math.round(x);
		if ( Math.abs(x - closestInteger) < 1e-10) {
			return String.format("%d", (int) closestInteger);
		}
		return String.format("%.4g", x);
	}
	
	static class WPMFormatter extends AbstractFormatter {
		@Override
		public Object stringToValue(String text) throws ParseException {
			return Double.parseDouble(text)/1e6;
		}
		@Override
		public String valueToString(Object value) throws ParseException {
			Double x = ((Double) value) * 1e6;
			return smartSigDigFormatter(x);
		}
	}

	static class SimpleFractionFormatter extends AbstractFormatter {
		@Override
		public Object stringToValue(String text) throws ParseException {
			return Double.parseDouble(text);
		}
		@Override
		public String valueToString(Object value) throws ParseException {
			Double x = (Double) value;
			return U.sf("%f", x);
		}
	}

}

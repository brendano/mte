package te.ui;

import java.text.ParseException;

import javax.swing.AbstractSpinnerModel;
import javax.swing.JFormattedTextField.AbstractFormatter;

import util.U;

@SuppressWarnings("serial")
public class SpinnerStuff {

	static class BaseMult {
		double base;
		int mult;
		BaseMult(double b, int m) { base=b; mult=m; }
		static BaseMult fromDouble(double x) {
			double b = Math.pow(10, Math.floor(Math.log10(x)));
			int m = (int) Math.round(x/b);
			return new BaseMult(b,m);
		}
	}

	static class MySM extends AbstractSpinnerModel {
		double curbase;
		int curmult;
		
		@Override
		public Object getValue() {
			return curbase*curmult;
		}
	
		@Override
		public void setValue(Object value) {
			double x = (double) value;
			BaseMult bm = BaseMult.fromDouble(x);
			curbase = bm.base;
			curmult = bm.mult;
			if (curmult*curbase==0) {
				curmult=1; curbase=1e-6;
			}
			fireStateChanged();
		}
	
		@Override
		public Object getNextValue() {
//			return next_1234567890();
			return next_1_3();
		}
		@Override
		public Object getPreviousValue() {
//			return prev_1234567890();
			return prev_1_3();
		}
		double next_1_3() {
			if (curmult < 1)
				return 1.0*curbase;
			else if (curmult >= 1 && curmult < 3)
				return 3.0*curmult*curbase;
			else if (curmult>=3 && curmult<10)
				return 1.0*(curbase*10);
			else {
				U.p("weird");
				return 1.0*(curbase*10);
			}
		}
		double prev_1_3() {
			if (curmult <= 1)
				return 3.0*curbase/10;
			else if (curmult > 1 && curmult <= 3)
				return 1.0*curbase;
			else if (curmult>3 && curmult<10)
				return 3.0*curbase;
			else {
				U.pf("weird %s %s\n", curmult, curbase);
				return 1.0*(curbase/10);
			}
		}
		double next_1234567890() {
			int newmult = curmult+1;
			double newbase = curbase;
			if (newmult >= 10) {
				newmult = 1;
				newbase *= 10;
			}
			return newbase*newmult;
		}
		double prev_1234567890() {
			int newmult = curmult-1;
			double newbase = curbase;
			if (newmult<=0) {
				newmult=9;
				newbase /= 10;
			}
			return newbase*newmult;
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

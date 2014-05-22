package ui;

import java.io.File;

import util.U;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class Configuration {

	/** 
	 * this is how shell "dirname" works:
	 * 
	 *    asdf/qwer.txt => asdf
	 * 
	 *    abcd => .
	 */
	static String dirname(String f) {
		String par = (new File(f)).getParent();
		return par==null ? "." : par;
	}
//	public static void main(String args[]) {
//		U.p(dirname(args[0]));
//	}
	
	public static void main(String args[]) { initWithConfig(null, args[0]); }
	
	public static void initWithConfig(Main main, String filename) {
		String dirOfConfFile = dirname(filename);
		File f = new File(filename);
		Config conf = ConfigFactory.parseFile(f);
		U.p(conf.toString());
		conf = conf.resolve();
		U.p(conf);
		
		if (conf.hasPath("data")) {
			
		}
		
	}

}

package ui;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;

import org.codehaus.jackson.JsonProcessingException;

import util.U;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import d.BadData;
import d.Schema;
import d.Schema.BadSchema;

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
	static boolean isAbsolute(String f) {
		return new File(f).isAbsolute();
	}
	
	public static void main(String args[]) throws JsonProcessingException, IOException {
		initWithConfig(null, args[0]);
	}
	
	static String resolvePath(String dirOfConfFile, String pathInConfFile) {
		if (isAbsolute(pathInConfFile)) {
			return pathInConfFile;
		}
		else {
			return new File(dirOfConfFile, pathInConfFile).toString();
		}
	}
	
	public static void initWithConfig(Main main, String filename) throws JsonProcessingException, IOException {
		String dirOfConfFile = dirname(filename);
		File f = new File(filename);
		Config conf = ConfigFactory.parseFile(f);
		conf = conf.resolve();
		U.p(conf);
		
		Function<String,String> resolve = (String f2) -> resolvePath(dirOfConfFile, f2);
		
		if (conf.hasPath("data")) {
			String path = resolvePath(dirOfConfFile, conf.getString("data"));
			try {
				main.corpus.loadJson(path);
			} catch (BadData | IOException e) {
				e.printStackTrace();
			}
			main.corpus.needsCovariateTypeConversion = true;
		}
		if (conf.hasPath("x")) {
			main.xattr = conf.getString("x");
		}
		if (conf.hasPath("y")) {
			main.yattr = conf.getString("y");
		}
		if (conf.hasPath("schema")) {
			main.corpus.schema = new Schema();
			try {
				String p = resolvePath(dirOfConfFile, conf.getString("schema"));
				main.corpus.schema.loadSchemaFromFile(p);
			} catch (BadSchema e) {
				e.printStackTrace();
			}
		}
		if (conf.hasPath("nlp_file")) {
			main.corpus.loadNLP(resolve.apply(conf.getString("nlp_file")));
		}
	}

}

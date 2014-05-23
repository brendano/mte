package ui;

import java.io.File;
import java.io.IOException;
import java.util.function.Function;

import org.codehaus.jackson.JsonProcessingException;

import util.U;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import d.Schema;
import exceptions.BadSchema;
import exceptions.BadConfig;
import exceptions.BadData;

import d.NLP;

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
	
	static String resolvePath(String dirOfConfFile, String pathInConfFile) {
		if (isAbsolute(pathInConfFile)) {
			return pathInConfFile;
		}
		else {
			return new File(dirOfConfFile, pathInConfFile).toString();
		}
	}
	static String resolvePathExists(String dirOfConfFile, String pathInConfFile) throws BadSchema {
		String f = resolvePath(dirOfConfFile,pathInConfFile);
		assertFileExists(f);
		return f;
	}
	
	static void assertFileExists(String filename) throws BadSchema {
		if ( ! (new File(filename)).exists()) {
			throw new BadSchema("File does not exist: " + filename);
		}
	}
	public static void initWithConfig(Main main, String filename) throws JsonProcessingException, IOException, BadConfig, BadSchema {
		
		// TODO, this function shouldn't be responsible for actually running potentially-expensive analysis routines.
		// it should queue them up somehow.
		
		String dirOfConfFile = dirname(filename);
		File _file = new File(filename);
		Config conf = ConfigFactory.parseFile(_file);
		conf = conf.resolve();
		U.p(conf);
		
		if (conf.hasPath("data")) {
			String path = resolvePathExists(dirOfConfFile, conf.getString("data"));
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
		if (main.xattr==null || main.yattr==null) throw new BadConfig("Need to specify both 'x' and 'y'"); 
		if (conf.hasPath("schema")) {
			Object schema = conf.getAnyRef("schema");
			if (schema instanceof String) {
				String sfilename = resolvePathExists(dirOfConfFile, (String) schema);
				main.corpus.schema.loadSchemaFromFile(sfilename);
			}
			else {
				main.corpus.schema.loadSchemaFromConfigObject(conf.getObject("schema"));
			}
		}

		if (!conf.hasPath("nlp_file") && !conf.hasPath("tokenizer")) {
			throw new BadSchema("Need to specify a tokenizer.");
//			U.p("Defaulting to tokenizer=StanfordTokenizer");
		}
		if (conf.hasPath("nlp_file")) {
			String f = resolvePathExists(dirOfConfFile, conf.getString("nlp_file"));
			main.corpus.loadNLP(f);
		}
		if (conf.hasPath("tokenizer")) {
			if (conf.hasPath("nlp_file")) throw new BadConfig("Don't specify both tokenizer and nlp_file");
			String tname = conf.getString("tokenizer");
			if (tname.equals("SimpleTokenizer")) {
				main.corpus.runTokenizer(NLP::simpleTokenize);
			} else if (tname.equals("StanfordTokenizer")) {
				main.corpus.runTokenizer(NLP::stanfordTokenize);
			}
			else throw new BadConfig("Unknown tokenizer: " + tname);
		}
	}

}

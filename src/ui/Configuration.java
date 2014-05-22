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
	
	public static void initWithConfig(Main main, String filename) throws JsonProcessingException, IOException, BadConfig, BadSchema {
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
			String p = resolvePath(dirOfConfFile, conf.getString("schema"));
			main.corpus.schema.loadSchemaFromFile(p);
		}
		if (conf.hasPath("nlp_file")) {
			main.corpus.loadNLP(resolve.apply(conf.getString("nlp_file")));
		}
		if (conf.hasPath("tokenizer")) {
			if (conf.hasPath("nlp_file")) throw new BadConfig("shouldn't specify both tokenizer and nlp_file");
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

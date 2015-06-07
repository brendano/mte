package te.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import te.data.DataLoader;
import te.data.NLP;
import te.exceptions.BadConfig;
import te.exceptions.BadData;
import te.exceptions.BadSchema;
import util.U;

import java.io.File;
import java.io.IOException;

public class Configuration {
	Config conf;
	String dirOfConfFile;
	Main main;
	String tokenizerName = null; 
			

	public static Configuration defaultConfiguration(Main main) {
		Configuration c = new Configuration();
		//c.conf = new EmptyConfig();
		c.conf = ConfigFactory.parseString("{}");
		c.tokenizerName = "StanfordTokenizer";
		c.main = main;
		return c;
	}
	/** 
	 * this is how shell "dirname" works:
	 * 
	 *    asdf/qwer.txt => asdf
	 * 
	 *    abcd => .
	 */
	public static String dirname(String f) {
		String par = (new File(f)).getParent();
		return par==null ? "." : par;
	}
	public static String basename(String f) {
		return (new File(f)).getName();
	}
	public static boolean isAbsolute(String f) {
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
	
	/** run this only once all the document texts are loaded */
	void doNLPBasedOnConfig() throws BadConfig, BadSchema, JsonProcessingException, IOException {
		if (conf.hasPath("nlp_file") && conf.hasPath("tokenizer"))
			throw new BadConfig("Don't specify both tokenizer and nlp_file");
		if (conf.hasPath("nlp_file")) {
			String f = resolvePathExists(dirOfConfFile, conf.getString("nlp_file"));
			main.corpus.loadNLP(f);
		}
		else {
			String tname = null;
			if (!conf.hasPath("tokenizer")) {
				U.p("Defaulting to tokenizer=StanfordTokenizer");
				tname = "StanfordTokenizer";
			}
			else if (conf.hasPath("tokenizer")) {
				tname = conf.getString("tokenizer");
			}
			if (tname.equals("WhitespaceTokenizer")) {
				main.corpus.runTokenizer(NLP::whitespaceTokenize);
			} else if (tname.equals("StanfordTokenizer")) {
				main.corpus.runTokenizer(NLP::stanfordTokenize);
			}
			else throw new BadConfig("Unknown tokenizer: " + tname);
		}
	}
	
	public void initWithConfig(Main _main, String filename, DataLoader dataloader) throws JsonProcessingException, IOException, BadConfig, BadSchema {
		
		// TODO in the future, this function shouldn't be responsible for actually running potentially-expensive analysis routines.
		// it should queue them up somehow.
		
		dirOfConfFile = dirname(filename);
		main = _main;
		File _file = new File(filename);
		conf = ConfigFactory.parseFile(_file);
		conf = conf.resolve();
		U.p(conf);
		
		if (conf.hasPath("indicatorize") && conf.getBoolean("indicatorize")) {
			main.afteranalysisCallback = () -> { main.corpus.indicatorize(); return null; };
		}
		if (conf.hasPath("data")) {
			String path = resolvePathExists(dirOfConfFile, conf.getString("data"));
			try {
				dataloader.loadJsonLines(path);
			} catch (BadData | IOException e) {
				e.printStackTrace();
			}
			main.corpus.needsCovariateTypeConversion = true;
		}
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


		
		// data view config should be set only after all data is loaded.
		
		if (conf.hasPath("x")) {
			if ( ! main.setXAttr(conf.getString("x"))) {
				assert false : "bad x variable " + conf.getString("x");
			}
		}
		if (conf.hasPath("y")) {
			if ( ! main.setYAttr(conf.getString("y"))) {
				assert false : "bad y variable" + conf.getString("y");
			}
		}

	}
}

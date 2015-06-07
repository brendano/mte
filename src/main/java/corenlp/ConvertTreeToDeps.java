package corenlp;

import java.io.*;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import util.JsonUtil;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.StringUtils;

//somewhat relevant: http://nlp.stanford.edu/software/TypedDependenciesDemo.java
//adapted from
//https://github.com/brendano/stanfordnlp-util/blob/master/src/Depper.java

public class ConvertTreeToDeps {
	static HeadFinder headfinder;
	static TreeFactory tree_factory;
	static TreebankLanguagePack tlp;
	static GrammaticalStructureFactory gsf;
	
	static void ensureInitialize() {
		if (tree_factory == null) {
			//		headfinder = new CollinsHeadFinder();
			tree_factory = new LabeledScoredTreeFactory();
			tlp = new PennTreebankLanguagePack();
			gsf = tlp.grammaticalStructureFactory();
		}
	}
	
	public static String readFile(String filename) throws FileNotFoundException { 
		File file = new File(filename);
		return new Scanner(file).useDelimiter("\\Z").next();
	}

	public static String join(AbstractCollection<String> s, String delimiter) {
		if (s == null || s.isEmpty()) return "";
		Iterator<String> iter = s.iterator();
		StringBuilder builder = new StringBuilder(iter.next());
		while( iter.hasNext() )
		{
			builder.append(delimiter).append(iter.next());
		}
		return builder.toString();
	}

	/** read a sexpr tree, as a string, into a Tree object */
	public static Tree readTreeFromString(String parseStr){
		ensureInitialize();
		TreeReader treeReader = new PennTreeReader(new StringReader(parseStr), tree_factory);
		Tree inputTree = null;
		try{
			inputTree = treeReader.readTree();
			treeReader.close();
		}catch(IOException e){
			e.printStackTrace();
			System.exit(-1);
		}
		return inputTree;
	}

	/** 
	 * returns a list of triples like:
	 *   [[rel,gov,child], [rel,gov,child], ...]
	 * where the relation name is a string, and gov and child are 0-based token indexes.  for example:
	 *   [["nsubj",1,0],["root",-1,1],["amod",4,2],["nn",4,3],["dobj",1,4],["number",7,6],["num",8,7],["prep_of",4,8],["appos",8,10],["num",10,11]]
	 * for the sentence
	 *   Takanohana received first prize money of ...
	 * the root of the tree is index -1.  the first word in the sentence is index 0.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static JsonNode convertTreeToDepsAsJson(Tree parse) {
		ensureInitialize();

		if (parse==null) {
			return JsonUtil.toJson(new ArrayList<>());
		}
		GrammaticalStructure gs = gsf.newGrammaticalStructure(parse);
		Collection<TypedDependency> deps1;
		deps1 = gs.typedDependenciesCCprocessed(true);
		
		// the dependency triples are mixed-type, so don't do generics declarations.
		List<List> depTriples = new ArrayList();
		for (TypedDependency d : deps1) {
			List jdep = new ArrayList();
			jdep.add(d.reln().getShortName());
			jdep.add(d.gov().index()-1);
			jdep.add(d.dep().index()-1);
			depTriples.add(jdep);
		}
		return JsonUtil.toJson(depTriples);
	}
	
	/**
	 * input: JSON object with the field "parse", e.g.:
	 *   {parse: "(sexpr of constit tree)" }
	 * add a new field, "deps", to it, so the new object is e.g.
	 *   {parse: "(sexpr of constit tree)", deps: [["nsubj",1,0],["root",-1,1], ...]}
	 */
	public static void addDepsToJsonSentence(JsonNode jsent_) {
		ObjectNode jsent = (ObjectNode) jsent_;
		String parseStr = jsent.get("parse").asText();
		Tree parse = readTreeFromString(parseStr);
		JsonNode deps = convertTreeToDepsAsJson(parse);
		jsent.set("deps", deps);
	}

	/**
	 * this is specific to my annotated-gigaword format, https://github.com/brendano/gigaword_conversion
	 * annogw has a sexpr for headlines and datelines, but no dependencies.
	 * this code adds them
	 */
	public static void addDepsToAnnoGW(String args[]) throws Exception {
		String line;
		BufferedReader brIn = new BufferedReader(new InputStreamReader(System.in));
		while ( (line=brIn.readLine()) != null) {
			if (line.trim().equals("")) continue;
			String[] parts = line.split("\t");
			
//			System.out.println("INPUT:\t" + parts[1]);
			
			String headerInfo_s = parts[1];
			JsonNode headerInfo = JsonUtil.parse(headerInfo_s);
			if (headerInfo.has("headline"))
				addDepsToJsonSentence(headerInfo.get("headline"));
			if (headerInfo.has("dateline"))
				addDepsToJsonSentence(headerInfo.get("dateline"));
			
			parts[1] = headerInfo.toString();
			System.out.println(StringUtils.join(parts, "\t"));
			
//			System.out.println("OUTPUT:\t" + parts[1]);
		}
	}
	
	public static void main(String args[]) throws Exception {
		addDepsToAnnoGW(args);
	}

}

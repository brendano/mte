package te.data;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;

import com.google.common.collect.ImmutableList;

import te.exceptions.BadData;
import te.ui.Configuration;
import util.BasicFileIO;
import util.JsonUtil;
import util.U;

/** represents the data during the loading process.  can incrementally add new stuff into it.
 * it has to infer what the full schema is and stuff like that.
 * it has to merge (join) information from multiple sources. 
 * this logic was previously in the Corpus class, but that was getting too crowded.
 * 
 * the loading routines defensively assume every record is a "partial" document,
 * then try to merge it into a pre-existing doc if exists.
 * otherwise, they start a new doc.
 */
public class DataLoader {
//	Schema schemaSoFar = new Schema();
	List<Document> docsInOriginalOrder = new ArrayList<>();
	Map<String,Document> docsById = new HashMap<>();
	
	static Set<String> SPECIAL_FIELDS;
	static {
		SPECIAL_FIELDS = new HashSet<>();
		SPECIAL_FIELDS.add("docid");
		SPECIAL_FIELDS.add("id");
		SPECIAL_FIELDS.add("text");
	}
	
	/** does NOT change internal state */
	int nextDocnum() { return docsInOriginalOrder.size() + 1; }
	
	String makeNewDefaultDocid(int docnumOfDoc) throws BadData {
		String newid = "doc" + docnumOfDoc;
		if (!docsById.containsKey(newid)) {
			return newid;
		}
		for (int trial=2; trial<1000; trial++) {
			newid = "doc" + nextDocnum() + "_" + trial;
			if (!docsById.containsKey(newid)) {
				return newid;
			}
		}
		throw new BadData("couldn't create a default docid");
	}
	
	String createNonConflictingKeyname(Document curDoc, String key) throws BadData {
		for (int n=2; n<10000; n++) {
			String newname = key + "_" + n;
			if (!curDoc.covariates.containsKey(newname)) {
				return newname;
			}
		}
		throw new BadData("Couldn't resolve conflicting keyname: " + key);
	}
	/** the doc could potentially be a partial document or subset of variables for it.
	 * note doc may have docid=null in some cases.
	 * 
	 * this method needs to be smart about merging against pre-existing information.
	 * this method WILL change the storage state (in most cases).
	 * @throws BadData 
	 */
	void addDocumentRecord(Document newDoc, boolean allowDocsWithoutIDs) throws BadData {
		if (newDoc.docid!=null && docsById.containsKey(newDoc.docid)) {
			// Merge
			Document curDoc = docsById.get(newDoc.docid);
			for (String newkey : newDoc.covariates.keySet()) {
				// should be smarter about name conflicts -- consistent name per source.
				// would require more refactoring so this function is aware of the data source being imported from
				// instead just do a simple local resolution
				String resolvedName = newkey;
				if (curDoc.covariates.containsKey(newkey)) {
					resolvedName = createNonConflictingKeyname(curDoc, newkey);
				}
				curDoc.covariates.put(resolvedName, curDoc.covariates.get(newkey));
				if (newDoc.text != null && curDoc.text==null) {
					curDoc.text = newDoc.text;
				} else if (newDoc.text != null && curDoc.text != null) {
					if ( ! newDoc.text.equals(curDoc.text)) {
						throw new BadData("Conflicting values for text from different data sources");	
					}
				}
			}
		} else {
			// New document!  Add it.
			newDoc.docnumOriginalOrder = nextDocnum();

			if (newDoc.docid==null && allowDocsWithoutIDs) {
				newDoc.docid = makeNewDefaultDocid(newDoc.docnumOriginalOrder);
			} else if (newDoc.docid==null && !allowDocsWithoutIDs) {
				throw new BadData("This data source requires all records to have a docid.");
			}
			assert newDoc.docid != null;
			assert ! docsById.containsKey(newDoc.docid);
			
			docsInOriginalOrder.add(newDoc);
			docsById.put(newDoc.docid, newDoc);
		}

	}
	
	/** does NOT change storage state.
	 * returns a Document with the information specificed in the JSON representation. */
	Document readDocFromJson(JsonNode j) throws BadData {
		if (!j.has("text"))
			throw new BadData("all docs must have a 'text' attribute");
		Document doc = new Document();
		JsonNode docidNode = j.has("docid") ? j.get("docid") : j.has("id") ? j.get("id") : null;
		doc.docid = docidNode==null ? null : docidNode.asText();
		doc.text = j.get("text").getTextValue();
		for (String origKey : ImmutableList.copyOf(j.getFieldNames())) {
			if (SPECIAL_FIELDS.contains(origKey)) {
				continue;
			}
			doc.covariates.put(origKey, j.get(origKey));
		}
		return doc;
	}

	public void loadJsonLines(String filename) throws BadData, IOException {
		for (String line : BasicFileIO.openFileLines(filename)) {
			Document doc = readDocFromJsonLine(line);
			addDocumentRecord(doc, true);
		}
	}

	Document readDocFromJsonLine(String line) throws IOException, BadData {
		String[] parts = line.split("\t");
		String docstr = parts[parts.length-1];
		JsonNode j;
		try {
			j = JsonUtil.readJson(docstr);
			Document doc = readDocFromJson(j);
			return doc;
		} catch ( JsonProcessingException e) {
			throw new BadData("invalid JSON: " + docstr);
		}

	}
	
	static boolean isValidDocid(String docid) {
		if (docid.trim().isEmpty()) return false;
		return true;
	}
	
	public void loadTextFileAsDocumentText(String filename) throws BadData, IOException {
		String name = Configuration.basename(filename);
		String docid = name.replace("\\.txt$", "");
		if ( ! isValidDocid(docid)) {
			throw new BadData(String.format("Bad docid: '%s'", docid));
		}
		String text = BasicFileIO.readFile(filename);
		Document d = new Document();
		d.text = text;
		d.docid = docid;
		addDocumentRecord(d, false);
	}
	
	public void loadTextFilesFromDirectory(String dirname) throws BadData, IOException {
		FileSystem FS = FileSystems.getDefault();
		int n = 0;
		U.p("Loading from directory " + dirname);
		try (DirectoryStream<Path> stream =
				Files.newDirectoryStream(FS.getPath(dirname), "*.txt")) {
			for (Path textfile : stream) {
				loadTextFileAsDocumentText(textfile.toString());
				n++;
			}
		}
		U.pf("%d files loaded from directory %s\n", n, dirname);
	}

}

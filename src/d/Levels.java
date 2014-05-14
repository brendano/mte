package d;
import java.util.*;

import org.codehaus.jackson.JsonNode;

import util.U;

public class Levels {
	public Map<Integer,Level> num2level = new HashMap<>();
	public Map<String,Level> name2level = new HashMap<>();
	
	public Collection<Level> levels() {
		return num2level.values();
	}
	public static class Level {
		public int number;
		public String name;
		public String longname;
		
		@Override
		public String toString() {
			return U.sf("Level(number=%d || name=%s || longname=%s)", number,name,longname);
		}
		public String displayName() {
			return longname!=null ? longname : name;
		}
	}
	public static class BadSchema extends Exception {
	    public BadSchema(String message) {
	        super(message);
	    }
	}
	
	public void loadJSON(JsonNode levelsinfo) throws BadSchema {
		if (!levelsinfo.isArray()) {
			throw new BadSchema("Levels info must be a JSON array.");
		}
		int index=-1;
		for (JsonNode jj : levelsinfo) {
			index++;
			Level lev = new Level();
			if (jj.isTextual()) {
				lev.number = index;
				lev.name = jj.asText();
				lev.longname = null;
			}
			else if (jj.isObject()) {
				lev.number = jj.has("number") ? jj.get("number").asInt() : index;
				lev.name = jj.has("name") ? jj.get("name").asText() : null;
				lev.longname = jj.has("longname") ? jj.get("longname").asText() : null;
			}
			else {
				throw new BadSchema(U.sf("Bad JSON array element: %s", jj.toString()));
			}
			if (lev.name==null && lev.longname!=null) lev.name = lev.longname;
			else if (lev.name==null && lev.longname==null) assert false : "need either code or name for every level";
			if (name2level.containsKey(lev.number)) {
				throw new BadSchema(U.sf("Repeated number %d in level %s", lev.number, lev));
			}
			name2level.put(lev.name, lev);
			num2level.put(lev.number, lev);
		}
	}

}

package d;
import java.util.*;

import org.codehaus.jackson.JsonNode;

public class Levels {
	public Map<Integer,Level> num2level = new HashMap<>();
	public Map<String,Level> code2level = new HashMap<>();
	
	public Collection<Level> levels() {
		return num2level.values();
	}
	public static class Level {
		public int number;
		public String code;
		public String name;
	}
	
	public void loadJSON(JsonNode levelsinfo) {
		for (JsonNode jj : levelsinfo) {
			Level lev = new Level();
			lev.number = jj.get("number").asInt();
			lev.code = jj.has("code") ? jj.get("code").asText() : null;
			lev.name = jj.has("name") ? jj.get("name").asText() : null;
			if (lev.code==null && lev.name!=null) lev.code = lev.name;
			else if (lev.code!=null && lev.name==null) lev.name=lev.code;
			else assert false : "need either code or name for every level";
			code2level.put(lev.code, lev);
			num2level.put(lev.number, lev);
		}
	}

}

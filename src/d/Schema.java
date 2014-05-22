package d;

import java.io.File;
import java.util.*;

import exceptions.BadSchema;

import org.codehaus.jackson.JsonNode;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import util.U;

public class Schema {
	public Map<String,ColumnInfo> columnTypes = new HashMap<>();
	
	public ColumnInfo column(String varname) {
		return columnTypes.get(varname);
	}
	public double getDouble(Document d, String attr) {
		ColumnInfo ci = this.columnTypes.get(attr);
		Object value = d.covariates.get(attr);
		if (value instanceof Integer || value instanceof Double) {
			return (Double) value;
		}
		if (value instanceof String && ci.dataType==DataType.CATEG) {
			assert ci.levels.name2level.containsKey(value) : String.format("unknown level '%s', not in vocabulary for %s", value, attr);
			return ci.levels.name2level.get(value).number;
		}
		U.p(columnTypes);
		U.p(attr + " || " + value + " || " + ci);
		assert false : "wtf";
		return -42;
	}
	public static enum DataType {
		NUMERIC, CATEG, BINARY;
	}
	
	public static class ColumnInfo {
		public DataType dataType;
		/** only for Categ or Ordinal, I think */
		public Levels levels;

		public boolean isCateg() {
			return dataType==DataType.CATEG;
		}
		public String toString() {
			String s = "" + dataType;
			if (dataType==DataType.CATEG) {
				s += "(" +levels.levels().size() + " levels)";
			}
			return s;
		}
		public ColumnInfo(String type) throws BadSchema {
			DataType dt = type.equals("numeric") ? DataType.NUMERIC :
				type.equals("categ")||type.equals("categorical")||type.equals("factor") ? DataType.CATEG :
				type.equals("binary")||type.equals("boolean") ? DataType.BINARY : null;
			if (dt==null) {
				throw new BadSchema(String.format("Unknown data type '%s'", type));
			}
			this.set(dt);
		}

		public ColumnInfo set(DataType type) {
			dataType = type;
			if (dataType==DataType.CATEG) {
				levels = new Levels();
			}
			return this;
		}
		
		public Object convertFromJson(JsonNode jj) {
			switch(dataType) {
			case NUMERIC:
				return jj.asDouble();
			case CATEG:
				return jj.asText();
			case BINARY:
				return jj.asBoolean();
			default:
				assert false;
				return null;
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public void loadSchemaFromFile(String schemaFile) throws BadSchema {
		U.p("file " + schemaFile);
		Config conf = ConfigFactory.parseFile(new File(schemaFile));
		if (conf.entrySet().size()==0) throw new BadSchema("Empty schema");
		U.p("Schema config: " + conf);
		for (Map.Entry<String,ConfigValue> e : conf.root().entrySet()) {
			String attrname = e.getKey();
			String type = null;
			ColumnInfo ci;
//			U.p(e.getKey() + " || " + e.getValue());
			if (e.getValue().valueType() == ConfigValueType.STRING) {
				type = (String) e.getValue().unwrapped();
				ci = new ColumnInfo(type);
			}
			else if (e.getValue().valueType() == ConfigValueType.OBJECT) {
				Map<String,Object> info = (Map<String,Object>) e.getValue().unwrapped();
				if ( ! info.containsKey("type")) throw new BadSchema("Need a 'type' attribute");
				type = (String) info.get("type");
				ci = new ColumnInfo(type);
				if (ci.dataType == DataType.CATEG && 
						(info.containsKey("values") || info.containsKey("levels"))) {
					Object _values = info.containsKey("values") ? info.get("values") : 
						info.containsKey("levels") ? info.get("levels") : null;
					List<String> values = (List<String>) _values;
					for (String value : values) {
						ci.levels.addLevel(value);
					}
				}
			}
			else {
				throw new BadSchema("Need a variable type description.");
			}
			assert ci != null;
			columnTypes.put(attrname, ci);
		}
		U.p("Covariate types, after schema loading: " + columnTypes);
	}
	
	public static class Levels {
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
		public Level addLevel(String name) {
			Level lev = new Level();
			lev.name = name;
			lev.number = num2level.size();
			num2level.put(lev.number,lev);
			name2level.put(lev.name,lev);
			return lev;
		}
		
//		public void loadJSON(JsonNode levelsinfo) throws BadSchema {
//			if (!levelsinfo.isArray()) {
//				throw new BadSchema("Levels info must be a JSON array.");
//			}
//			int index=-1;
//			for (JsonNode jj : levelsinfo) {
//				index++;
//				Level lev = new Level();
//				if (jj.isTextual()) {
//					lev.number = index;
//					lev.name = jj.asText();
//					lev.longname = null;
//				}
//				else if (jj.isObject()) {
//					lev.number = jj.has("number") ? jj.get("number").asInt() : index;
//					lev.name = jj.has("name") ? jj.get("name").asText() : null;
//					lev.longname = jj.has("longname") ? jj.get("longname").asText() : null;
//				}
//				else {
//					throw new BadSchema(U.sf("Bad JSON array element: %s", jj.toString()));
//				}
//				if (lev.name==null && lev.longname!=null) lev.name = lev.longname;
//				else if (lev.name==null && lev.longname==null) assert false : "need either code or name for every level";
//				if (name2level.containsKey(lev.number)) {
//					throw new BadSchema(U.sf("Repeated number %d in level %s", lev.number, lev));
//				}
//				name2level.put(lev.name, lev);
//				num2level.put(lev.number, lev);
//			}
//		}

	}

}

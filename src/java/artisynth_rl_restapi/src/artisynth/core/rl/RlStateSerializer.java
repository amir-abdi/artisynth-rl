package artisynth.core.rl;

import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class RlStateSerializer<T> implements JsonSerializer<Object> {

	private JsonElement serializeRlState(RlState object) {
		Gson gson = new Gson();

		JsonObject array_jo = new JsonObject();
		for (RlComponent comp : object.getRlComponents()) {
			array_jo.add(comp.getName(), gson.toJsonTree(comp));
		}
		
		JsonObject array_props = new JsonObject();
		for (RlProp prop : object.getProps()) {
			array_props.add(prop.getName(), gson.toJsonTree(prop.getProp()));
		}
		
		JsonObject result = new JsonObject();
		result.add("observation", array_jo);	
		result.add("properties", array_props);
		result.add("excitations", gson.toJsonTree(object.getExcitations()));
		result.add("muscleForces", gson.toJsonTree(object.getMuscleForces()));
		result.addProperty("time", object.getTime());
		
		return result;
	}

	@Override
	public JsonElement serialize(Object src, Type typeOfSrc, JsonSerializationContext context) {
		return serializeRlState((RlState) src);
	}

}

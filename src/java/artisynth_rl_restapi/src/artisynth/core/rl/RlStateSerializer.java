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
		for (RlComponent comp : object.getState()) {
			array_jo.add(comp.getName(), gson.toJsonTree(comp));
		}

		JsonObject result = new JsonObject();
		result.add("observation", array_jo);
		return result;
	}

	@Override
	public JsonElement serialize(Object src, Type typeOfSrc, JsonSerializationContext context) {
		return serializeRlState((RlState) src);
	}

}

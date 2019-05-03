package artisynth.core.rl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import spark.ResponseTransformer;

public class JsonUtil {

	public static String toJson(Object object) {

		GsonBuilder gsonBuilder = new GsonBuilder();
		RlStateSerializer<RlState> serializer = new RlStateSerializer<RlState>();
		gsonBuilder.registerTypeAdapter(RlState.class, serializer);

		Gson customGson = gsonBuilder.create();
		return customGson.toJson(object);
	}

	public static ResponseTransformer json() {
		return JsonUtil::toJson;
	}
}

package artisynth.core.rl;

import spark.Request;
import spark.Response;
import spark.Route;
import static artisynth.core.rl.JsonUtil.*;
import static spark.Spark.*;

import com.google.gson.Gson;

public class RlRestApi {
	RlControllerInterface rlController;
	int serverPort;
	// private static org.apache.logging.log4j.Logger logger =
	// LogManager.getLogger(RlRestApi.class);

	public RlRestApi(RlControllerInterface rlController, int serverPort) {
		this.rlController = rlController;
		this.serverPort = serverPort;
		port(serverPort);

		spark.Spark.get("/", (request, response) -> "Welcome to the REST API for the RL solutions in ArtiSynth.");
		spark.Spark.post("/reset",
				(request, response) -> rlController.resetState(Boolean.parseBoolean(request.body())), json());
		spark.Spark.post("/setSeed",
				(request, response) -> rlController.setSeed(Integer.parseInt(request.body())), json());
		spark.Spark.get("/state", (request, response) -> rlController.getState(), json());
		spark.Spark.get("/obsSize", (request, response) -> rlController.getObservationSize(), json());
		spark.Spark.get("/stateSize", (request, response) -> rlController.getStateSize(), json());
		spark.Spark.get("/actionSize", (request, response) -> rlController.getActionSize(), json());

		spark.Spark.get("/excitations", (request, response) -> rlController.getExcitations(), json());
		spark.Spark.post("/excitations", setExcitations, json());

		after((req, res) -> {
			res.type("application/json");
		});

		exception(IllegalArgumentException.class, (e, req, res) -> {
			res.status(400);
			res.body(toJson(new ResponseError(e)));
		});
	}

	public Route setExcitations = (Request request, Response response) -> {
		Log.debug("setExcitations length:" + request.contentLength() + " type: " + request.contentType());
		Gson gson = new Gson();	
		Log.debug(request.body());
		RlMuscleProps rlExcitations = gson.fromJson(request.body(), RlMuscleProps.class);
		Log.debug(rlExcitations);
		Log.debug(rlExcitations.getProps());
		///----------------- 
		RlState nextState = this.rlController.setExcitations(rlExcitations.getProps());
		return nextState;
	};
}
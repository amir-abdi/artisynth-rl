package artisynth.core.rl;

import java.util.ArrayList;

public interface RlControllerInterface {

	RlState getState();
	int getActionSize();
	int getObservationSize();
	String resetState();
	void setExcitations(ArrayList<Double> excitations);
	ArrayList<Double> getExcitations();
}

package artisynth.core.rl;

import java.util.ArrayList;

public interface RlControllerInterface {

	RlState getState();
	int getActionSize();
	int getObservationSize();
	int getStateSize();
	String resetState();
	RlState setExcitations(ArrayList<Double> excitations);
	ArrayList<Double> getExcitations();
}
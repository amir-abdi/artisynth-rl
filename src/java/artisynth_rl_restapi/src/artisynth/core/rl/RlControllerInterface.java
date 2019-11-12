package artisynth.core.rl;

import java.util.ArrayList;

public interface RlControllerInterface {

	RlState getState();
	int getActionSize();
	int getObservationSize();
	int getStateSize();
	String resetState(boolean setExcitationsZero);
	RlState setExcitations(ArrayList<Double> excitations);
	ArrayList<Double> getExcitations();
	ArrayList<Double> getMuscleForces();
	String setSeed(int seed);
}
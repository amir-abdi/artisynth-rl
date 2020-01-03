package artisynth.core.rl;

import java.util.ArrayList;

public interface RlModelInterface {
	 void resetState();
	 void addRlController();
	 void parseArgs(String[] args);
	 ArrayList<RlProp> getRlProps();
	 RlTargetControllerInterface getTargetMotionController();
	 double getTime();
}

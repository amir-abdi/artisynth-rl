package artisynth.core.rl;

public interface RlModelInterface {
	 void resetState();
	 void addRlController();
	 void parseArgs(String[] args);	 
	 RlTargetControllerInterface getTargetMotionController();
}

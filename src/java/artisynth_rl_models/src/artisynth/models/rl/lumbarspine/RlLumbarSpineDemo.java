package artisynth.models.rl.lumbarspine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import artisynth.core.mechmodels.MuscleExciter;
import artisynth.core.rl.RlModelInterface;
import artisynth.core.rl.RlProp;
import artisynth.core.rl.RlTargetControllerInterface;
import artisynth.core.utils.Utils;
import artisynth.core.rl.RlController;
import artisynth.core.rl.Log;

public class RlLumbarSpineDemo extends InvLumbarSpineDemo implements RlModelInterface {

	protected int port = 8080;

	RandomTargetController targetMotionController;
	RlController rlTrack;	

	public RlLumbarSpineDemo() {
		this("RlLumbarSpineAngular");
	}

	public RlLumbarSpineDemo(String name) {
		super(name, false);
	}

	@Override
	public void build(String[] args) throws IOException {
		super.build(args);
		parseArgs(args);
		addRlController();
	}

	@Override
	public void parseArgs(String[] args) {
		Map<String, String> dictionary = Utils.parseArgs(args);
		if (dictionary.containsKey("-port"))
			this.port = Integer.parseInt(dictionary.get("-port"));		
	}

	// ------------ Implement RlModelInterface -------------
	@Override
	public void addRlController() {
		rlTrack = new RlController(mech, (RlModelInterface) this, "InvTracker", this.port);		

		for (String src_rb_str : sourceRigidBodies) {
			rlTrack.addMotionTarget(mech.rigidBodies().get(src_rb_str));
		}

		for (MuscleExciter m : mex) {
			rlTrack.addExciter(m);
		}

		targetMotionController = new RandomTargetController(rlTrack.getMotionTargets());

		addController(targetMotionController);
		addController(rlTrack);
		addControlPanel(rlTrack.getRlControlPanel());
		addControlPanel(rlTrack.getMuscleControlPanel());
		
		addTrackingRotationMonitors(rlTrack.getMotionTargets());
	}

	@Override
	public void resetState() {
		targetMotionController.reset();
	}

	@Override
	public RlTargetControllerInterface getTargetMotionController() {
		return this.targetMotionController;
	}
	
	public ArrayList<RlProp> getRlProps() {
		ArrayList<RlProp> props = new ArrayList<RlProp>();
		return props;
	}
	
	public double getTime() {
		return 0.;
	}


}

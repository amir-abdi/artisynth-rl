package artisynth.models.rl;

import java.io.IOException;

import artisynth.core.mechmodels.MuscleExciter;
import artisynth.core.rl.RlModelInterface;
import artisynth.core.rl.RlController;
import artisynth.core.rl.Log;

import artisynth.models.rl.inverseFDAT.InvLumbarSpineModel;

public class RlLumbarSpineModel extends InvLumbarSpineModel implements RlModelInterface {

	protected int port = 8080;

	RandomTargetController targetMotionController;
	RlController rlTrack;

	public RlLumbarSpineModel() {
		this("RlLumbarSpineAngular");
	}

	public RlLumbarSpineModel(String name) {
		super(name, false);
	}

	@Override
	public void build(String[] args) throws IOException {
		super.build(args);
		parseArgs(args);
		addRlController();
	}

	private void parseArgs(String[] args) {
		try {
			for (int i = 0; i < args.length; i += 2) {
				System.out.print(args[i] + " " + args[i + 1] + "\n");
				if (args[i].equals("-port")) {
					this.port = Integer.parseInt(args[i + 1]);
				}
			}
		} catch (Exception e) {
			Log.log("Args not properly parsed");
		}
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

		addTrackingRotationMonitors(rlTrack.getMotionTargets());
	}

	@Override
	public void resetState() {
		targetMotionController.reset();
	}

}

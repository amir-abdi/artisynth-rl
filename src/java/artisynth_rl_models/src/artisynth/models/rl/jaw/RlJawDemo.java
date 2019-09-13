package artisynth.models.rl.jaw;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import artisynth.core.driver.Main;
import artisynth.core.femmodels.FemMarker;
import artisynth.core.femmodels.FemModel3d;
import artisynth.core.inverse.ForceTargetTerm;
import artisynth.core.inverse.TrackingController;
import artisynth.core.mechmodels.BodyConnector;
import artisynth.core.mechmodels.CollidableBody;
import artisynth.core.mechmodels.ExcitationComponent;
import artisynth.core.mechmodels.Frame;
import artisynth.core.mechmodels.FrameMarker;
import artisynth.core.mechmodels.MechModel;
import artisynth.core.mechmodels.MotionTargetComponent;
import artisynth.core.mechmodels.MultiPointMuscle;
import artisynth.core.mechmodels.Muscle;
import artisynth.core.mechmodels.MuscleExciter;
import artisynth.core.mechmodels.PlanarConnector;
import artisynth.core.mechmodels.Point;
import artisynth.core.mechmodels.RigidBody;
import artisynth.core.modelbase.ControllerBase;
import artisynth.core.modelbase.StepAdjustment;
import artisynth.core.probes.NumericInputProbe;
import artisynth.core.probes.NumericOutputProbe;
import artisynth.core.rl.RlController;
import artisynth.core.rl.RlModelInterface;
import artisynth.core.rl.RlTargetControllerInterface;
import artisynth.core.util.ArtisynthPath;
import artisynth.core.utils.Utils;
import artisynth.core.workspace.DriverInterface;
import artisynth.core.workspace.RootModel;

import artisynth.models.rl.lumbarspine.InvLumbarSpineDemo.RandomTargetController;
import artisynth.models.rl.lumbarspine.InvLumbarSpineDemo.SimpleTargetController;
//import artisynth.models.bruxism.Activation_Renderer;
//import artisynth.models.bruxism.Bruxism;
//import artisynth.models.elasticCollisions.PenetrationRenderer;
import maspack.matrix.AxisAngle;
import maspack.matrix.Point3d;
import maspack.matrix.RigidTransform3d;
import maspack.matrix.SparseBlockMatrix;
import maspack.matrix.Vector3d;
import maspack.matrix.VectorNd;
import maspack.properties.Property;
import maspack.render.Renderer.LineStyle;
import maspack.render.Renderer.PointStyle;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;
import artisynth.core.femmodels.FemModel3d;
import artisynth.core.rl.Log;

public class RlJawDemo extends RootModel implements RlModelInterface {

	// Excitors' names:
	// my: mylohyoid (pm (posterior mylohyoid) + am (anterior mylohyoid))
	// lp: lateral pterygoid (sp, ip)
	// t : temporalis (at, mt, pt)
	// m: masseter (dm + sm)
	// hopen: hyoid open (pm, am, ad (anterior digastric), gh (genio hyoid))
	// pro: protrusive (sp, ip)
	// open: hopen + pro
	// close: t + dm + sm
	// mp: medial pterygoid

	RlController rlTrack;
	RandomTargetController targetMotionController;
	JawFemModel myJawModel;

	private int port;
	private boolean withDisc = false;
	private boolean condyleConstraints = false;

	protected String workingDirname = "data";

	public RlJawDemo() {
	}

	public RlJawDemo(String name) {
		super(null);
	}

	@Override
	public void build(String[] args) throws IOException {
		super.build(args);
		parseArgs(args);
		setWorkingDir();

		myJawModel = new JawFemModel("jawmodel", withDisc, condyleConstraints);
		addModel(myJawModel);
		// TODO: set back to 0.001 if became unstable --> it became unstable in 0.01
		getRoot(this).setMaxStepSize(0.001);

		addRlController();
	}

	public void setWorkingDir() {
		if (workingDirname == null)
			return;
		// set default working directory to repository location
		File workingDir = new File(ArtisynthPath.getSrcRelativePath(RlJawDemo.class, workingDirname));
		ArtisynthPath.setWorkingDir(workingDir);
	}

	public StepAdjustment advance(double t0, double t1, int flags) {
			
		return super.advance(t0, t1, flags);
	}

	@Override
	public void resetState() {
		targetMotionController.reset();
	}

	@Override
	public void addRlController() {
		rlTrack = new RlController(myJawModel, (RlModelInterface) this, "RlTracker", this.port);

		// rlTrack.addMotionTarget(myJawModel.rigidBodies().get("jaw"));
		rlTrack.addMotionTarget(myJawModel.frameMarkers().get("lowerincisor"));

		for (String exCategoryName : myJawModel.muscleExciterCategoryNames) {
			if (exCategoryName == myJawModel.muscleExciterCategoryNames[2]) { // only add bilateral
				for (MuscleExciter mex : myJawModel.myMuscleExciterCategories.get(exCategoryName)) {
					Log.log("rlTrack Exciter> " + exCategoryName + ":" + mex.getName());
					rlTrack.addExciter(mex);
				}
			}
		}

		targetMotionController = new RandomTargetController(rlTrack.getMotionTargets());

		addController(targetMotionController);
		addController(rlTrack);
		addControlPanel(rlTrack.getRlControlPanel());
		addControlPanel(rlTrack.getMuscleControlPanel());
	}

	@Override
	public void parseArgs(String[] args) {
		Map<String, String> dictionary = Utils.parseArgs(args);
		if (dictionary.containsKey("-port"))
			this.port = Integer.parseInt(dictionary.get("-port"));
		if (dictionary.containsKey("-disc"))
			this.withDisc = Boolean.parseBoolean(dictionary.get("-disc"));
		if (dictionary.containsKey("-condyleConstraints"))
			this.condyleConstraints = Boolean.parseBoolean(dictionary.get("-condyleConstraints"));
	}

	public class RandomTargetController extends ControllerBase implements RlTargetControllerInterface {
		public Boolean reset = false;
		public Boolean trialRun = false;
		Random r = new Random();
		private int time_pos_updated = -1;

		Frame mandible = null;
		Point lowerincisor = null;
		Point3d openMouthPositionLowerIncisor = new Point3d(2.6639861, -85.972497, -75.207516);
		Point3d closedMouthPositionLowerIncisor = new Point3d(2.3216955, -92.172079, -41.505347);

		public RandomTargetController(ArrayList<MotionTargetComponent> list) {
			Log.log(list.get(0).getName());
			if (list.get(0) instanceof Frame) {
				mandible = (Frame) list.get(0);
			} else if (list.get(0) instanceof Point) {
				lowerincisor = (Point) list.get(0);
			}
			r.setSeed(123);
		}

		public void reset() {
			this.reset = true;
		}

		public void apply(double t0, double t1) {
			if (t0 > -1) {

				// TODO: make reset random configurable
				if (trialRun) {
					if ((int) t0 != time_pos_updated && (int) t0 % 3 == 1) {
						time_pos_updated = (int) t0;
						resetRefPosition(false);
					}
				} else if (reset) {
					reset = false;
					resetRefPosition(false);
				}
			}
		}

		private double getRandom(double radius) {
			return (r.nextDouble() - 0.5) * radius;
		}

		private void resetRefPosition(Boolean random) {
			// TODO: complete the reset pos implementation
			if (mandible != null) {
				if (random) {
					double radius = 5;
					Point3d posAdd = new Point3d(getRandom(radius), getRandom(radius), getRandom(radius));
					Point3d currentPos = mandible.getPosition();
					posAdd.add(currentPos);
					mandible.setPosition(posAdd);
				} else {
					throw new NotImplementedException();
				}
			} else if (lowerincisor != null) {
				if (random) {
					throw new NotImplementedException();
				} else {
					// swtich between open and close mouth
					if (lowerincisor.getPosition().z < -60) //open
						lowerincisor.setPosition(closedMouthPositionLowerIncisor);
					else
						lowerincisor.setPosition(openMouthPositionLowerIncisor);
					rlTrack.setExcitationsZero();
				}
			}
		}
	}

	@Override
	public RlTargetControllerInterface getTargetMotionController() {
		return targetMotionController;
	}
}
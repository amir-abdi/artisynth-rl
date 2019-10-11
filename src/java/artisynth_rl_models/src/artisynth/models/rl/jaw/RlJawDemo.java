package artisynth.models.rl.jaw;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

import org.python.modules.struct;

import artisynth.core.mechmodels.Frame;
import artisynth.core.mechmodels.MotionTargetComponent;
import artisynth.core.mechmodels.MuscleExciter;
import artisynth.core.mechmodels.Point;
import artisynth.core.modelbase.ControllerBase;
import artisynth.core.modelbase.StepAdjustment;
import artisynth.core.rl.Log;
import artisynth.core.rl.RlController;
import artisynth.core.rl.RlModelInterface;
import artisynth.core.rl.RlTargetControllerInterface;
import artisynth.core.util.ArtisynthPath;
import artisynth.core.utils.Utils;
import artisynth.core.workspace.RootModel;
import maspack.matrix.Point3d;
import maspack.matrix.Vector3d;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

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
	private boolean condylarCapsule = false;

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

		myJawModel = new JawFemModel("jawmodel", withDisc, condyleConstraints, condylarCapsule);
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
		rlTrack = new RlController(myJawModel, (RlModelInterface) this, "RlTracker", this.port, false);

		// rlTrack.addMotionTarget(myJawModel.rigidBodies().get("jaw"));
		rlTrack.addMotionTarget(myJawModel.frameMarkers().get("lowerincisor"));

		for (String exCategoryName : myJawModel.muscleExciterCategoryNames) {
			if (exCategoryName == myJawModel.muscleExciterCategoryNames[0]) { // only add bilateral
				for (MuscleExciter mex : myJawModel.myMuscleExciterCategories.get(exCategoryName)) {
					Log.debug("rlTrack Exciter> " + exCategoryName + ":" + mex.getName());
					rlTrack.addExciter(mex);
				}
			}
		}
//		rlTrack.addExciter(myJawModel.getMuscleExciters().get("bi_open"));
//		rlTrack.addExciter(myJawModel.getMuscleExciters().get("bi_close"));

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
		if (dictionary.containsKey("-condylarCapsule"))
			this.condylarCapsule = Boolean.parseBoolean(dictionary.get("-condylarCapsule"));
	}

	enum JawModelType {
		withCondylarConstraints, withCondylarCapsule
	};

	enum JawPositions {
		closedPosition(2.6199824, -94.095219, -40.749265, JawModelType.withCondylarConstraints),
		openPosition(3.7713994, -85.668177, -75.45326, JawModelType.withCondylarConstraints, 7.0),
		restPosition(2.432348, -89.850281, -46.341909, JawModelType.withCondylarConstraints),		
		pureRotationalOpeningPosition(3.1654528, -84.767872, -59.862705, JawModelType.withCondylarConstraints),
		rightLaterotrusivePosition(-12.188396, -96.733508, -44.460555, JawModelType.withCondylarConstraints),
		leftLaterotrusivePosition(16.940234, -97.129743, -43.485869, JawModelType.withCondylarConstraints),
		retrudedContactPosition(2.8772912, -91.449043, -41.952765, JawModelType.withCondylarConstraints), // CentricRelation
		edgeToEdgePosition(-0.024246108, -96.801937, -45.99113, JawModelType.withCondylarConstraints),
		protrusivePosition(0.84730021, -107.36706, -40.258819, JawModelType.withCondylarConstraints);

		JawModelType jawModelType;
		double x, y, z;
		double weight;

		private JawPositions(double x, double y, double z, JawModelType jawModelType) {
			this(x, y, z, jawModelType, 1);
		}
		
		private JawPositions(double x, double y, double z, JawModelType jawModelType, double weight) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.jawModelType = jawModelType;
			this.weight = weight;
		}

		public Vector3d getPosition() {
			return new Vector3d(x, y, z);
		}

		public double getWeight() {
			return weight;
		}
		
		public JawModelType getJawModelType() {
			return this.jawModelType;
		}
		
		
	}

	// closedPosition 2.6199824 -94.095219 -40.749265
	// restPosition 2.432348 -89.850281 -46.341909
	// openPosition 3.7713994 -85.668177 -75.45326
	// pureRotationalOpeningPosition 3.1654528 -84.767872 -59.862705

	// rightLaterotrusivePosition -12.188396 -96.733508 -44.460555
	// lefttLaterotrusivePosition 16.940234 -97.129743 -43.485869

	// retrudedContactPosition 2.8772912 -91.449043 -41.952765 // CentricRelation
	// edgeToEdgePosition -0.024246108 -96.801937 -45.99113
	// protrusivePosition 0.84730021 -107.36706 -40.258819

	public class RandomTargetController extends ControllerBase implements RlTargetControllerInterface {
		public Boolean reset = false;
		public Boolean trialRun = false;
		Random r = new Random();
		private int time_pos_updated = -1;

		Frame mandible = null;
		Point lowerincisor = null;
		
		JawPositions jawPositionsArr[] = JawPositions.values();

		Point3d openMouthPositionLowerIncisor = new Point3d(2.6639861, -85.972497, -75.207516);
		Point3d closedMouthPositionLowerIncisor = new Point3d(2.3216955, -92.172079, -41.505347);
//		Point3d restPositionLowerIncisor = new Point3d(2.4890594, -90.656961, -44.354956);

		public RandomTargetController(ArrayList<MotionTargetComponent> list) {
			Log.debug(list.get(0).getName());
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

		private Point3d getRandomPoint(JawPositions jawPositions[]) {
			Point3d result = new Point3d();
			double weights_sum = 0;
			for (int i=0; i<jawPositions.length; ++i) {
				JawPositions jp = jawPositions[i];
				double w = r.nextDouble();
				result.add(jp.getPosition().scale(jp.getWeight() * w));
				weights_sum += jp.getWeight() * w;
			}
			result.scale(1/weights_sum);
			return result;
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
					lowerincisor.setPosition(getRandomPoint(jawPositionsArr));
										
//					int index = r.nextInt(jawPositionsArr.length);
//					lowerincisor.setPosition(jawPositionsArr[index].getPosition());
//					Log.info(jawPositionsArr[index]);
					
					// swtich between open and close mouth
//					if (lowerincisor.getPosition().z < -60) // open
////						lowerincisor.setPosition(restPositionLowerIncisor);
//						lowerincisor.setPosition(closedMouthPositionLowerIncisor);					
//					else
//						lowerincisor.setPosition(openMouthPositionLowerIncisor);
					
				}
			}
		}
	}

	@Override
	public RlTargetControllerInterface getTargetMotionController() {
		return targetMotionController;
	}
}
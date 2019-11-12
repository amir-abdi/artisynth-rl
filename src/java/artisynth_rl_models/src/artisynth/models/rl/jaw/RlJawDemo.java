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
			if (exCategoryName == myJawModel.muscleExciterCategoryNames[0]) {
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
		
		if (condyleConstraints && condylarCapsule) { 
			Log.info("The jaw can't be constrained by both condyleConstraints and condylarCapsule."
					+ "Please choose only one, or only condylarCapsule would be considered.");
			condyleConstraints = false;
		}
	}

	enum JawModelType {
		withCondylarConstraints, withCondylarCapsule
	};

	enum JawPositions {
		closedPosition(2.5096367, -93.705412, -40.160793, JawModelType.withCondylarConstraints),
		openPosition(3.3689007, -85.471129, -74.432114, JawModelType.withCondylarConstraints, 4.0),
		openPosition2(3.3689007, -85.471129, -74.432114, JawModelType.withCondylarConstraints, 4.0),
//		restPosition(2.262723, -88.375605, -50.954255, JawModelType.withCondylarConstraints),		
		pureRotationalOpeningPosition(3.8681168, -86.448567, -54.012106, JawModelType.withCondylarConstraints),
		rightLaterotrusivePosition(-10.043439, -96.073347, -46.498435, JawModelType.withCondylarConstraints, 3.0),
		leftLaterotrusivePosition(16.127454, -96.769054, -44.305393, JawModelType.withCondylarConstraints, 3.0),
		retrudedContactPosition(2.8224137, -91.190817, -41.398978, JawModelType.withCondylarConstraints), // CentricRelation
		edgeToEdgePosition(1.9139294, -95.848331, -45.183162, JawModelType.withCondylarConstraints),
		protrusivePosition(1.2662906, -103.68767, -40.740465, JawModelType.withCondylarConstraints, 3.0);

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

	public class RandomTargetController extends ControllerBase implements RlTargetControllerInterface {
		public Boolean reset = false;
		public Boolean trialRun = false;
		private int time_pos_updated = -1;

		Frame mandible = null;
		Point lowerincisor = null;

		JawPositions jawPositionsArr[] = JawPositions.values();

		public RandomTargetController(ArrayList<MotionTargetComponent> list) {
			Log.debug(list.get(0).getName());
			if (list.get(0) instanceof Frame) {
				mandible = (Frame) list.get(0);
			} else if (list.get(0) instanceof Point) {
				lowerincisor = (Point) list.get(0);
			}
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
			return (rlTrack.random.nextDouble() - 0.5) * radius;
		}

		private Point3d getRandomPoint(JawPositions jawPositions[]) {
			Point3d result = new Point3d();
			double weights_sum = 0;

			if (rlTrack.random.nextDouble() < 0.25) {
				// choose only two of the border points
				int index1 = rlTrack.random.nextInt(jawPositions.length);
				int index2 = rlTrack.random.nextInt(jawPositions.length); //it's ok to be the same as index1
				double w1 = rlTrack.random.nextDouble();
				double w2 = rlTrack.random.nextDouble();
				result.add(jawPositions[index1].getPosition().scale(w1));
				result.add(jawPositions[index2].getPosition().scale(w2));
				result.scale(1 / (w1 + w2));				
			} else {
				// choose a combination of the border points
				for (int i = 0; i < jawPositions.length; ++i) {
					JawPositions jp = jawPositions[i];
					double w = rlTrack.random.nextDouble() * jp.getWeight();
					result.add(jp.getPosition().scale(w));
					weights_sum += w;
				}
				result.scale(1 / weights_sum);				
			}
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
				}
			}
		}
	}

	@Override
	public RlTargetControllerInterface getTargetMotionController() {
		return targetMotionController;
	}
}
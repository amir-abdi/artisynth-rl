package artisynth.models.rl.jaw;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Random;

import org.python.modules.struct;

import com.github.sardine.model.Collection;

import artisynth.core.mechmodels.Frame;
import artisynth.core.mechmodels.MotionTargetComponent;
import artisynth.core.mechmodels.MuscleExciter;
import artisynth.core.mechmodels.Point;
import artisynth.core.modelbase.ControllerBase;
import artisynth.core.modelbase.StepAdjustment;
import artisynth.core.monitors.AsyncDualBufferMonitor;
import artisynth.core.rl.Log;
import artisynth.core.rl.RlController;
import artisynth.core.rl.RlModelInterface;
import artisynth.core.rl.RlProp;
import artisynth.core.rl.RlTargetControllerInterface;
import artisynth.core.util.ArtisynthPath;
import artisynth.core.utils.Utils;
import artisynth.core.workspace.RootModel;
import maspack.matrix.Point3d;
import maspack.matrix.Vector3d;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class RlJawDemo extends RootModel implements RlModelInterface {

	private double SLEEP_SECONDS = 0.5;
	private double RANDOM_JAW_RADIUS_POSITION = 5;
	private String[] TEST_SEQUENCES = {"main", "visualization", "video", "symmetric", "semiPosselt"}; 
	private int TEST_SEQUENCE_INDEX = 0;

	RlController rlTrack;
	TargetController targetMotionController;
	JawFemModel myJawModel;

	private int port;
	private boolean withDisc = false;
	private boolean condyleConstraints = false;
	private boolean condylarCapsule = false;
	private Double myTime;
	protected AsyncDualBufferMonitor monitorOcclusalForces = new AsyncDualBufferMonitor();
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
		if (rlTrack.getTest()) {
			monitorOcclusalForces.setProp(myJawModel.getOcclusalForces());
		}
		myTime = t0;
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
			// Add all the exciters independently. Alternatives are bilateral or grouped.
			if (exCategoryName == myJawModel.muscleExciterCategoryNames[0]) {
				for (MuscleExciter mex : myJawModel.myMuscleExciterCategories.get(exCategoryName)) {
					Log.debug("rlTrack Exciter> " + exCategoryName + ":" + mex.getName());
					rlTrack.addExciter(mex);
				}
			}
		}

		targetMotionController = new TargetController(rlTrack.getMotionTargets(), rlTrack.getTest());

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

	public ArrayList<RlProp> getRlProps() {
		ArrayList<RlProp> props = new ArrayList<RlProp>(1);

		// Only return forces at test time
		// If forces are included in training, change the following block of code.
		if (rlTrack.getTest()) {
			RlProp occlusalForces = new RlProp("occlusalForces");
			ArrayList<Double> forces = (ArrayList<Double>) monitorOcclusalForces.getProp();
			if (forces != null) {
				occlusalForces.setProp(forces);
				props.add(occlusalForces);
			}
		}

		return props;
	}

	enum JawModelType {
		withCondylarConstraints, withCondylarCapsule
	};

	interface JawPositionInterface {
		public Vector3d getPosition();

		public JawModelType getJawModelType();

		public String getName();
	}

	class JawPosition implements JawPositionInterface {
		JawModelType jawModelType;
		double x, y, z;
		String name;

		public JawPosition(double x, double y, double z, JawModelType jawModelType, String name) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.jawModelType = jawModelType;
			this.name = name;
		}

		public JawPosition(JawPositionEnum jp) {
			this.x = jp.x;
			this.y = jp.y;
			this.z = jp.z;
			this.jawModelType = jp.jawModelType;
			this.name = jp.name;
		}

		public Vector3d getPosition() {
			return new Vector3d(x, y, z);
		}

		public JawModelType getJawModelType() {
			return this.jawModelType;
		}

		public JawPosition interpolate(JawPositionEnum position, double weight) {
			assert weight < 1.0;
			assert weight > 0;
			double my_weight = 1 - weight;
			JawPosition new_position = new JawPosition(my_weight * x + weight * position.x,
					my_weight * y + weight * position.y, my_weight * z + weight * position.z, jawModelType,
					this.name + "_" + position.getName() + "@" + weight);
			return new_position;
		}

		public String getName() {
			return this.name;
		}
	}

	enum JawPositionEnum implements JawPositionInterface {
		closedPosition(2.5096367, -93.705412, -40.160793, JawModelType.withCondylarConstraints, "closedPosition"),
		openPosition(3.3689007, -85.471129, -74.432114, JawModelType.withCondylarConstraints, "openPosition", 4.0),
		openPosition2(3.3689007, -85.471129, -74.432114, JawModelType.withCondylarConstraints, "openPosition", 4.0),
		restPosition(2.2536774, -88.633096, -50.842464, JawModelType.withCondylarConstraints, "restPosition"),
		pureRotationalOpeningPosition(3.8681168, -86.448567, -54.012106, JawModelType.withCondylarConstraints,
				"pureRotationalOpeningPosition"),
		rightLaterotrusivePosition(-10.043439, -96.073347, -46.498435, JawModelType.withCondylarConstraints,
				"rightLaterotrusivePosition", 3.0),
		leftLaterotrusivePosition(16.127454, -96.769054, -44.305393, JawModelType.withCondylarConstraints,
				"leftLaterotrusivePosition", 3.0),
		rightLaterotrusivePosition2(-10.043439, -96.073347, -46.498435, JawModelType.withCondylarConstraints,
				"rightLaterotrusivePosition", 3.0),
		leftLaterotrusivePosition2(16.127454, -96.769054, -44.305393, JawModelType.withCondylarConstraints,
				"leftLaterotrusivePosition", 3.0),
		retrudedContactPosition(2.8224137, -91.190817, -41.398978, JawModelType.withCondylarConstraints,
				"retrudedContactPosition"), // CentricRelation
		edgeToEdgePosition(1.9139294, -95.848331, -45.183162, JawModelType.withCondylarConstraints,
				"edgeToEdgePosition"),
		protrusivePosition(1.2662906, -103.68767, -40.740465, JawModelType.withCondylarConstraints,
				"protrusivePosition", 3.0);

		JawModelType jawModelType;
		double x, y, z;
		double weight;
		String name;

		private JawPositionEnum(double x, double y, double z, JawModelType jawModelType, String name) {
			this(x, y, z, jawModelType, name, 1);
		}

		private JawPositionEnum(double x, double y, double z, JawModelType jawModelType, String name, double weight) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.jawModelType = jawModelType;
			this.name = name;
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

		public String getName() {
			return this.name;
		}
	}

	public double getTime() {
		return myTime;
	}

	public class TargetController extends ControllerBase implements RlTargetControllerInterface {
		public Boolean reset = false;
		double radius = RANDOM_JAW_RADIUS_POSITION; // for random jaw positioning

		Frame mandible = null;
		Point lowerincisor = null;
		JawPositionEnum jawPositionsArr[] = JawPositionEnum.values();

		// properties of the testTrial run
		private ArrayList<JawPositionInterface> testPositions = new ArrayList<JawPositionInterface>();
		int testPositionIndex = 0;
		double sleepSeconds = SLEEP_SECONDS;

		public TargetController(ArrayList<MotionTargetComponent> list, boolean isTest) {
			Log.info("in targetContgroller: is test" + rlTrack.getTest());
			Log.debug(list.get(0).getName());
			if (list.get(0) instanceof Frame) {
				mandible = (Frame) list.get(0);
			} else if (list.get(0) instanceof Point) {
				lowerincisor = (Point) list.get(0);
			}

			Log.info("Testing with sequence: " + TEST_SEQUENCES[TEST_SEQUENCE_INDEX]);
			switch (TEST_SEQUENCE_INDEX) {
			case 0:
				setMainTestSequence();
				break;
			case 1:
				setVisualizationSequence();
				break;
			case 2:
				setVideoSequence();
				break;
			case 3:
				setSymmetricSequence();
				break;
			case 4:
				setSemiPosseltSequence();
				break;
			}			
		}

		public void reset() {
			this.reset = true;
		}

		// TODO: make reset random configurable
		public void apply(double t0, double t1) {
			// the initial position is the closed position
			if (t0 > -1) {
				if (reset) {
					reset = false;
					// even if reset was off, and we were in the testTrialRun mode, reset every
					// goToNextPointInterval seconds
					if (rlTrack.getTest()) {
						// starting from sleepSecond, reset the position every goToNextPointInterval
						// seconds. Until then, keep the jaw at position index 0.
						if (t0 >= sleepSeconds) {
							Log.info(t0);
							testPositionIndex++;
							resetRefPosition(false);

						} else {
							testPositionIndex = 0;
							resetRefPosition(false);
						}

					} else {
						resetRefPosition(false);
					}
				}
			}
		}

		private double getRandom(double radius) {
			return (rlTrack.random.nextDouble() - 0.5) * radius;
		}

		private Point3d getRandomPoint(JawPositionEnum jawPositions[]) {
			Point3d result = new Point3d();
			double weights_sum = 0;

			if (rlTrack.random.nextDouble() < 0.5) {
				// choose only two of the border points
				int index1 = rlTrack.random.nextInt(jawPositions.length);
				int index2 = rlTrack.random.nextInt(jawPositions.length); // it's ok to be the same as index1
				double w1 = rlTrack.random.nextDouble();
				double w2 = rlTrack.random.nextDouble();
				result.add(jawPositions[index1].getPosition().scale(w1));
				result.add(jawPositions[index2].getPosition().scale(w2));
				result.scale(1 / (w1 + w2));
			} else {
				// choose a combination of the border points
				for (int i = 0; i < jawPositions.length; ++i) {
					JawPositionEnum jp = jawPositions[i];
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
				if (rlTrack.getTest()) {
					throw new NotImplementedException();
				} else {
					if (random) {
						Point3d posAdd = new Point3d(getRandom(radius), getRandom(radius), getRandom(radius));
						Point3d currentPos = mandible.getPosition();
						posAdd.add(currentPos);
						mandible.setPosition(posAdd);
					} else {
						throw new NotImplementedException();
					}
				} // if (this.testTrialRun)
			} else if (lowerincisor != null) {
				if (rlTrack.getTest()) {
					if (testPositionIndex >= testPositions.size()) {
						Log.info("Test trial complete!");
						return;
					}
					JawPositionInterface jpi = testPositions.get(testPositionIndex);
					Log.info(jpi.getName());
					lowerincisor.setPosition(new Point3d(jpi.getPosition()));

				} else {
					if (random) {
						throw new NotImplementedException();
					} else {
						lowerincisor.setPosition(getRandomPoint(jawPositionsArr));
					}
				} // if (this.testTrialRun)
			}
		}

		private void setMainTestSequence() {
			testPositions.add(JawPositionEnum.closedPosition);
			testPositions.add(
					new JawPosition(JawPositionEnum.openPosition).interpolate(JawPositionEnum.closedPosition, 0.2));
			testPositions.add(JawPositionEnum.closedPosition);
			testPositions.add(JawPositionEnum.restPosition);
			testPositions.add(JawPositionEnum.rightLaterotrusivePosition);
			testPositions.add(JawPositionEnum.restPosition);
			testPositions.add(JawPositionEnum.leftLaterotrusivePosition);
			testPositions.add(JawPositionEnum.restPosition);
			testPositions.add(JawPositionEnum.retrudedContactPosition);
			testPositions.add(JawPositionEnum.restPosition);
			testPositions.add(JawPositionEnum.edgeToEdgePosition);
			testPositions.add(JawPositionEnum.restPosition);
			testPositions.add(JawPositionEnum.pureRotationalOpeningPosition);
			testPositions.add(
					new JawPosition(JawPositionEnum.openPosition).interpolate(JawPositionEnum.closedPosition, 0.5));
			testPositions.add(new JawPosition(JawPositionEnum.leftLaterotrusivePosition)
					.interpolate(JawPositionEnum.pureRotationalOpeningPosition, 0.5));
			testPositions.add(new JawPosition(JawPositionEnum.rightLaterotrusivePosition)
					.interpolate(JawPositionEnum.pureRotationalOpeningPosition, 0.5));
			testPositions.add(new JawPosition(JawPositionEnum.openPosition)
					.interpolate(JawPositionEnum.pureRotationalOpeningPosition, 0.5));
			testPositions.add(new JawPosition(JawPositionEnum.openPosition)
					.interpolate(JawPositionEnum.pureRotationalOpeningPosition, 0.2));
			testPositions.add(new JawPosition(JawPositionEnum.openPosition)
					.interpolate(JawPositionEnum.pureRotationalOpeningPosition, 0.8));
			testPositions.add(new JawPosition(JawPositionEnum.leftLaterotrusivePosition)
					.interpolate(JawPositionEnum.openPosition, 0.3));
			testPositions.add(new JawPosition(JawPositionEnum.rightLaterotrusivePosition)
					.interpolate(JawPositionEnum.openPosition, 0.3));
			testPositions.add(
					new JawPosition(JawPositionEnum.openPosition).interpolate(JawPositionEnum.protrusivePosition, 0.3));
			// 22
		}

		private void setSemiPosseltSequence() {
			testPositions.add(JawPositionEnum.openPosition);
			testPositions.add(
					new JawPosition(JawPositionEnum.openPosition).interpolate(JawPositionEnum.protrusivePosition, 0.3));
			testPositions.add(
					new JawPosition(JawPositionEnum.openPosition).interpolate(JawPositionEnum.protrusivePosition, 0.6));
			testPositions.add(
					new JawPosition(JawPositionEnum.openPosition).interpolate(JawPositionEnum.protrusivePosition, 0.8));
			testPositions.add(JawPositionEnum.protrusivePosition);
			testPositions.add(JawPositionEnum.edgeToEdgePosition);
			testPositions.add(JawPositionEnum.closedPosition);
			testPositions.add(JawPositionEnum.retrudedContactPosition);
			testPositions.add(JawPositionEnum.pureRotationalOpeningPosition);
			testPositions.add(JawPositionEnum.restPosition);
			testPositions.add(JawPositionEnum.rightLaterotrusivePosition);
			testPositions.add(JawPositionEnum.restPosition);
			testPositions.add(JawPositionEnum.leftLaterotrusivePosition);
			// 13
		}

		private void setVisualizationSequence() {
			testPositions.add(JawPositionEnum.openPosition);
			testPositions.add(JawPositionEnum.edgeToEdgePosition);
			testPositions.add(JawPositionEnum.rightLaterotrusivePosition);
			testPositions.add(JawPositionEnum.edgeToEdgePosition);
			testPositions.add(JawPositionEnum.leftLaterotrusivePosition);
			testPositions.add(JawPositionEnum.closedPosition);
			testPositions.add(JawPositionEnum.pureRotationalOpeningPosition);
			testPositions.add(JawPositionEnum.closedPosition);
			testPositions.add(JawPositionEnum.edgeToEdgePosition);
			testPositions.add(JawPositionEnum.retrudedContactPosition);
			testPositions.add(JawPositionEnum.restPosition);
			testPositions.add(JawPositionEnum.closedPosition);
			testPositions.add(JawPositionEnum.openPosition);
			testPositions.add(JawPositionEnum.edgeToEdgePosition);
			testPositions.add(
					new JawPosition(JawPositionEnum.openPosition).interpolate(JawPositionEnum.protrusivePosition, 0.9));
			// 15
		}

		private void setVideoSequence() {
			testPositions.add(JawPositionEnum.closedPosition);
			testPositions.add(JawPositionEnum.pureRotationalOpeningPosition);
			testPositions.add(
					new JawPosition(JawPositionEnum.openPosition).interpolate(JawPositionEnum.protrusivePosition, 0.1));
			testPositions.add(JawPositionEnum.edgeToEdgePosition);
			testPositions.add(JawPositionEnum.rightLaterotrusivePosition);
			testPositions.add(JawPositionEnum.edgeToEdgePosition);
			testPositions.add(JawPositionEnum.leftLaterotrusivePosition);
			testPositions.add(JawPositionEnum.edgeToEdgePosition);
			testPositions.add(JawPositionEnum.closedPosition);

			testPositions.add(JawPositionEnum.closedPosition);
			testPositions.add(
					new JawPosition(JawPositionEnum.openPosition).interpolate(JawPositionEnum.protrusivePosition, 0.9));
			testPositions.add(JawPositionEnum.restPosition);
			// 12
		}

		private void setSymmetricSequence() {
			testPositions.add(JawPositionEnum.closedPosition);
			testPositions.add(JawPositionEnum.pureRotationalOpeningPosition);
			testPositions.add(JawPositionEnum.edgeToEdgePosition);
			testPositions.add(
					new JawPosition(JawPositionEnum.openPosition).interpolate(JawPositionEnum.protrusivePosition, 0.2));
			testPositions.add(JawPositionEnum.restPosition);
			testPositions.add(JawPositionEnum.edgeToEdgePosition);
			testPositions.add(
					new JawPosition(JawPositionEnum.openPosition).interpolate(JawPositionEnum.protrusivePosition, 0.8));
			testPositions.add(JawPositionEnum.edgeToEdgePosition);
			testPositions.add(JawPositionEnum.closedPosition);
			testPositions.add(
					new JawPosition(JawPositionEnum.openPosition).interpolate(JawPositionEnum.protrusivePosition, 0.1));
			testPositions.add(JawPositionEnum.closedPosition);
			testPositions.add(
					new JawPosition(JawPositionEnum.openPosition).interpolate(JawPositionEnum.protrusivePosition, 0.9));
			testPositions.add(JawPositionEnum.restPosition);
			// 13
		}
	}

	@Override
	public RlTargetControllerInterface getTargetMotionController() {
		return targetMotionController;
	}

}
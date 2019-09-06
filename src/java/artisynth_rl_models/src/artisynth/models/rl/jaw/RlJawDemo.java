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
import artisynth.core.femmodels.FemModel3d;

public class RlJawDemo extends RootModel implements RlModelInterface {
	RlController rlTrack;
	RandomTargetController targetMotionController;
	JawFemModel myJawModel;

	ArrayList<String> MuscleAbbreviation = new ArrayList<String>();
	String EXCITERS_PATH = "inv.txt";
	protected String workingDirname = "data";
	List<String> ForceTargetNames = Arrays.asList("Brux_M6"); // "CANINE" or "LBITE"

	double t = 0.75; // 0.5 prot; 0.75 open; 0.7 brux

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

		myJawModel = new JawFemModel("jawmodel", true);
		addModel(myJawModel);
		// TODO: set back to 0.001 if became unstable --> it became unstable in 0.01
		getRoot(this).setMaxStepSize(0.001);
	
		MuscleAbbreviation = getMuscleNames();
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

		artisynth.core.mechmodels.Point lowerincisor = myJawModel.frameMarkers().get("lowerincisor");
		if (t0 == 0 || t0 == 0.1) {
			System.out.println("t0=" + t0 + ":   " + lowerincisor.getPosition());
		} else if (t1 == 0.75) {
			System.out.println("t1=" + t1 + ":  " + lowerincisor.getPosition() + "\n");
		}
		return super.advance(t0, t1, flags);
	}

	@Override
	public void resetState() {
		targetMotionController.reset();
	}

	private ArrayList<String> getMuscleNames() throws FileNotFoundException {
		Scanner s = new Scanner(
				new FileReader(ArtisynthPath.getSrcRelativePath(RlJawDemo.class, "data/" + EXCITERS_PATH)));
		ArrayList<String> names = new ArrayList<String>();
		while (s.hasNext()) {
			names.add(s.next());
		}
		s.close();
		return names;
	}

	@Override
	public void addRlController() {
		rlTrack = new RlController(myJawModel, (RlModelInterface) this, "InvTracker", this.port);

		rlTrack.addMotionTarget(myJawModel.rigidBodies().get("jaw"));
		
		for (String name : MuscleAbbreviation)
        {
           Muscle m = (Muscle) myJawModel.axialSprings().get(name);
           if (m==null){
              MultiPointMuscle   mm = (MultiPointMuscle) myJawModel.multiPointSprings ().get (name);
              rlTrack.addExciter(mm);              
           }
           else 
        	   rlTrack.addExciter(m);
        }	
		
		targetMotionController = new RandomTargetController(rlTrack.getMotionTargets());

		addController(targetMotionController);
		addController(rlTrack);
		addControlPanel(rlTrack.getRlControlPanel());
		addControlPanel(rlTrack.getMuscleControlPanel());
	}

	private int port;

	@Override
	public void parseArgs(String[] args) {
		Map<String, String> dictionary = Utils.parseArgs(args);
		if (dictionary.containsKey("-port"))
			this.port = Integer.parseInt(dictionary.get("-port"));
	}
	
	public class RandomTargetController extends ControllerBase implements RlTargetControllerInterface {
		public Boolean reset = false;
		public Boolean trialRun = false;
		Random r = new Random();
		private int time_pos_updated = -1;
		
		Frame mandible;

		public RandomTargetController(ArrayList<MotionTargetComponent> list) {
			mandible = (Frame)list.get(0);
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
						resetRefPosition();
					}
				} else if (reset) {
					reset = false;
					resetRefPosition();
				}

			}

		}

		private double getRandom(double radius) {
			return (r.nextDouble() - 0.5) * radius;
		}
			
		private void resetRefPosition() {
			// TODO: complete the reset pos implementation
			double radius = 5; 
			Point3d posAdd = new Point3d(getRandom(radius), getRandom(radius), getRandom(radius));
			Point3d currentPos = mandible.getPosition();
			posAdd.add(currentPos);
			mandible.setPosition(posAdd);						
		}
	}

	@Override
	public RlTargetControllerInterface getTargetMotionController() {		
		return targetMotionController;
	}
}

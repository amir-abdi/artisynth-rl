package artisynth.models.rl.base.jaw;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import artisynth.core.driver.Main;
import artisynth.core.femmodels.FemMarker;
import artisynth.core.femmodels.FemModel3d;
import artisynth.core.inverse.ForceTargetTerm;
import artisynth.core.inverse.TrackingController;
import artisynth.core.mechmodels.BodyConnector;
import artisynth.core.mechmodels.CollidableBody;
import artisynth.core.mechmodels.ExcitationComponent;
import artisynth.core.mechmodels.FrameMarker;
import artisynth.core.mechmodels.MultiPointMuscle;
import artisynth.core.mechmodels.Muscle;
import artisynth.core.mechmodels.MuscleExciter;
import artisynth.core.mechmodels.PlanarConnector;
import artisynth.core.modelbase.StepAdjustment;
import artisynth.core.probes.NumericInputProbe;
import artisynth.core.probes.NumericOutputProbe;
import artisynth.core.util.ArtisynthPath;
import artisynth.core.workspace.DriverInterface;
import artisynth.core.workspace.RootModel;
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

public class JawBaseDemo extends RootModel {
	JawFemModel myJawModel;
	TrackingController myTrackingController;
	ArrayList<String> MuscleAbbreviation = new ArrayList<String>();
	String excitersFile = "inv2.txt";
	protected String workingDirname = "data";
	List<String> ForceTargetNames = Arrays.asList("Brux_M6"); // "CANINE" or "LBITE"

	double t = 0.75; // 0.5 prot; 0.75 open; 0.7 brux

	public JawBaseDemo() {		
	}

	public JawBaseDemo(String name) {
		super(null);
	}

	@Override
	public void build(String[] args) throws IOException {
		super.build(args);
		setWorkingDir();

		myJawModel = new JawFemModel("jawmodel");
		addModel(myJawModel);
		getRoot(this).setMaxStepSize(0.001);

		addOpening();
		for (double i = 0.01; i <= t; i = i + 0.01) {
			addWayPoint(i);
		}
		addBreakPoint(t);
	}

	public void addOpening() throws IOException {
		for (BodyConnector p : myJawModel.bodyConnectors()) {

			if (p.getName().equals("BiteICP") == false) {
				p.setEnabled(false);
				p.getRenderProps().setVisible(false);
			}

		}

		MuscleExciter mex = myJawModel.getMuscleExciters().get("bi_open");

		NumericInputProbe probe = new NumericInputProbe(mex, "excitation",
				ArtisynthPath.getSrcRelativePath(JawFemModel.class, "/data/input_activation.txt"));
		probe.setStartStopTimes(0, 0.5);
		probe.setName("Opening Muscle Activation");
		addInputProbe(probe);
	}

	public void setWorkingDir() {
		if (workingDirname == null)
			return;
		// set default working directory to repository location
		File workingDir = new File(ArtisynthPath.getSrcRelativePath(JawBaseDemo.class, workingDirname));
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
}

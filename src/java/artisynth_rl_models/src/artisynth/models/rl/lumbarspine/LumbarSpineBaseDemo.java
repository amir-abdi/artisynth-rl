package artisynth.models.rl.lumbarspine;

import java.awt.Color;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSeparator;

import maspack.function.Function1x3;
import maspack.geometry.MeshFactory;
import maspack.geometry.PolygonalMesh;
import maspack.matrix.AffineTransform3d;
import maspack.matrix.AxisAngle;
import maspack.matrix.Matrix3d;
import maspack.matrix.Point3d;
import maspack.matrix.RigidTransform3d;
import maspack.matrix.Vector3d;
import maspack.properties.PropertyList;
import maspack.render.RenderProps;
import maspack.render.Renderer.LineStyle;
import maspack.render.Renderer.PointStyle;
import maspack.render.Renderer.Shading;
import maspack.spatialmotion.Wrench;
import artisynth.core.gui.ControlPanel;
import artisynth.core.inverse.TargetPoint;
import artisynth.core.inverse.TrackingController;
import artisynth.core.materials.AxialMuscleMaterial;
import artisynth.core.materials.HeuerOffLinFM;
import artisynth.core.materials.MasoudMillardLAM;
import artisynth.core.mechmodels.AxialSpring;
import artisynth.core.mechmodels.BodyConnector;
import artisynth.core.mechmodels.FrameMarker;
import artisynth.core.mechmodels.FrameSpring;
import artisynth.core.mechmodels.MechModel;
import artisynth.core.mechmodels.MeshComponent;
import artisynth.core.mechmodels.MeshComponentList;
import artisynth.core.mechmodels.MotionTargetComponent;
import artisynth.core.mechmodels.MultiPointMuscle;
import artisynth.core.mechmodels.MultiPointSpring;
import artisynth.core.mechmodels.Muscle;
import artisynth.core.mechmodels.MuscleExciter;
import artisynth.core.mechmodels.PointForce;
import artisynth.core.mechmodels.RevoluteJoint;
import artisynth.core.mechmodels.RigidBody;
import artisynth.core.mechmodels.RigidMeshComp;
import artisynth.core.mechmodels.SolidJoint;
import artisynth.core.modelbase.ComponentList;
import artisynth.core.modelbase.Controller;
import artisynth.core.modelbase.ControllerBase;
import artisynth.core.modelbase.Model;
import artisynth.core.modelbase.Monitor;
import artisynth.core.modelbase.StepAdjustment;
import artisynth.core.monitors.FrameMarkerMonitor;
import artisynth.core.monitors.MultiPointMuscleMonitor;
import artisynth.core.monitors.MuscleExciterMonitor;
import artisynth.core.monitors.MuscleMonitor;
import artisynth.core.monitors.RigidBodyMonitor;
import artisynth.core.monitors.TargetPointMonitor;
import artisynth.core.util.ArtisynthPath;
import artisynth.core.workspace.DriverInterface;
import artisynth.core.workspace.PullController;
import artisynth.core.workspace.RootModel;
import artisynth.models.rl.lumbarspine.MuscleParser.MuscleInfo;
import artisynth.models.rl.lumbarspine.MuscleParser.MusclePoint;

public class LumbarSpineBaseDemo extends RootModel implements ItemListener {

	protected MechModel mech;
	protected double forceByIAP = 0.060; //
	protected double validationForce;
	protected double crateForce;
	protected double farCrateForce;
	protected ArrayList<MuscleInfo> allMusclesInfo;
	protected ArrayList<Muscle> allMuscles = new ArrayList<Muscle>();
	protected ArrayList<MultiPointMuscle> allMultiPointMuscles = new ArrayList<MultiPointMuscle>();
	protected ArrayList<String> allMuscleGeneralNames = new ArrayList<String>();
	protected ArrayList<String> allMultiPointMuscleGeneralNames = new ArrayList<String>();
	protected ArrayList<String> thorax = new ArrayList<String>();
	protected ArrayList<String> lumbar = new ArrayList<String>();
	protected ArrayList<String> pelvis = new ArrayList<String>();
	protected ArrayList<String> includedMuscleGroupNames = new ArrayList<String>();
	protected ArrayList<String> bodyMassCenters = new ArrayList<String>();
	protected ArrayList<String> muscleInjuryLevels = new ArrayList<String>();
	protected ArrayList<ArrayList<String>> LTpL = new ArrayList<ArrayList<String>>(),
			LTpT = new ArrayList<ArrayList<String>>(), ILpL = new ArrayList<ArrayList<String>>(),
			ILpT = new ArrayList<ArrayList<String>>(), MF = new ArrayList<ArrayList<String>>(),
			LD = new ArrayList<ArrayList<String>>(), QL = new ArrayList<ArrayList<String>>(),
			Ps = new ArrayList<ArrayList<String>>(), RA = new ArrayList<ArrayList<String>>(),
			EO = new ArrayList<ArrayList<String>>(), IO = new ArrayList<ArrayList<String>>();

	// Geometries
	//protected String meshesPath = "../../../../../../data/lumbarSpine/";
	public final String[] muscleGroupNames = { "Latissimus_Dorsi", "Rectus_Abdominis", "Int_Oblique", "Ext_Oblique",
			"Multifidus", "Psoas_major", "quadratus_lumborum", "longissimus_thoracis_pars_lumborum",
			"longissimus_thoracis_pars_thoracis", "iliocostalis_lumborum_pars_lumborum",
			"iliocostalis_lumborum_pars_thoracis" };
	public final String[] muscleGroupBriefNames = { "LD", "RA", "IO", "EO", "MF", "Ps", "QL", "LTpL", "LTpT", "ILpL",
			"ILpT" };
	public final double[] sarcomereLengths = { 2.3, 2.83, 2.83, 2.83, 2.27, 3.11, 2.38, 2.31, 2.31, 2.37, 2.37 };
	boolean[] muscleVisible;

	public ComponentList<MuscleExciter> mex = new ComponentList<MuscleExciter>(MuscleExciter.class, "mex",
			"muscleExcitation");;

	public RenderProps boneRenderProps;
	public RenderProps fmRenderPropsCOM;
	public RenderProps fmRenderPropsMuscle;
	public RenderProps fmRenderPropsCOF;

	public LumbarSpineBaseDemo(String name) {

		super(name);

		BodyConnector.useOldDerivativeMethod = true;
		mech = new MechModel("mech");
		makeThorax();
		addRigidBodies();

		addAbdomen();
		loadMusclesInfo();
		addFrameMarkers();
		makeMuscles();

		addMuscles();
		addExciters();

		meshTransformation();
		addFrameSprings();
		addFixators();
		doLevelCalculations();
		mech.setGravity(0, -9.81, 0);
		addModel(mech);
	}

	// The version for transfering everything to the center of mass instead of
	// centroid.
	public void meshTransformation() {
		Vector3d COM = new Vector3d();
		PolygonalMesh mesh;

		for (RigidBody rb : mech.rigidBodies()) {
			COM = new Vector3d(rb.getCenterOfMass().x, rb.getCenterOfMass().y, rb.getCenterOfMass().z);

			if (rb.getName().equals("thorax")) {
				RigidBody rcb = mech.rigidBodies().get("thorax");
				MeshComponentList<RigidMeshComp> rcbMeshes = rcb.getMeshComps();
				COM.negate();

				// Translating all meshes so that thorax_centroid is set as the
				// Origin of
				// thorax frame ()
				for (String str : thorax) {
					MeshComponent mc = rcbMeshes.get(str);
					mesh = (PolygonalMesh) mc.getMesh();
					mesh.transform(new RigidTransform3d(COM, AxisAngle.IDENTITY));
				}
				fmTransformation(rcb.getName(), COM);
				inertiaTransformation(rcb.getName(), COM);

				// Translating the origin thorax frame so that its World-coordinate is set to
				// original world coordinate of thorax-centroid
				COM.negate();
				rcb.transformGeometry(new RigidTransform3d(COM, AxisAngle.IDENTITY));
			} else {
				mesh = new PolygonalMesh();
				mesh = rb.getSurfaceMesh();
				COM.negate();
				mesh.transform(new RigidTransform3d(COM, AxisAngle.IDENTITY));
				fmTransformation(rb.getName(), COM);
				inertiaTransformation(rb.getName(), COM);
				COM.negate();
				rb.transformGeometry(new RigidTransform3d(COM, AxisAngle.IDENTITY));
			}
		}
	}

	// ********************************************************
	// ******************* Adding ATTACH DRIVER ***************
	// ********************************************************
	public void attach(DriverInterface driver) {
		super.attach(driver);

		try {
			driver.getViewer().setUpVector(new Vector3d(0, 1, 0));
		} catch (Exception e) {
			System.out.println("No GUI");
		}

		setViewerEye(new Point3d(-0.0760112, 1.13265, 1.50791));
		setViewerCenter(new Point3d(-0.0760112, 1.13265, 0.00102557));
		addHeadNeckWeight();

		addIntraAbdominalPressure();
		addValidationForce();

		addController(new ThetaController());
		addControlPanels();
	}

	// ********************************************************
	// ******************* Adding Control Panels **************
	// ********************************************************
	// Properties
	public static PropertyList myProps = new PropertyList(LumbarSpineBaseDemo.class, RootModel.class);

	public PropertyList getAllPropertyInfo() {
		return myProps;
	}

	static {
		myProps.add("boneAlpha * *", "transparency of bones", 0.2, "[0,1] NW");
		myProps.add("frameMarkerAlpha * *", "transparency of frame marker", 0.0, "[0,1] NW");
		myProps.add("forceByIAP * *", "force equivalent of IAP", 0.090, "[0,1] NW");
		myProps.add("validationForce * *", "horizontal force used for validation", 0.0, "[0,1] NW");
		myProps.add("crateForce * *", "weight of crate", 0.0, "[0,1] NW");
		myProps.add("farCrateForce * *", "weight of farCrate", 0.0, "[0,1] NW");
		myProps.addReadOnly("torsojntForces *", "forces");
		myProps.addReadOnly("l1L2Forces *", "Axialforces");
		myProps.addReadOnly("l2L3Forces *", "Axialforces");
		myProps.addReadOnly("l3L4Forces *", "Axialforces");
		myProps.addReadOnly("l4L5Forces *", "Axialforces");
		myProps.addReadOnly("l5S1Forces *", "Axialforces");
	}

	public void addControlPanels() {
		addVisibilityControlPanel();
	}

	public void addVisibilityControlPanel() {
		ControlPanel myPanel = new ControlPanel("Visibility", "LiveUpdate");
		myPanel.addWidget(new JLabel("Render Properties:"));
		myPanel.addWidget("    Bone", this, "boneAlpha");
		myPanel.addWidget("    Frame Marker", this, "frameMarkerAlpha");

		myPanel.addWidget(new JSeparator());
		myPanel.addWidget(new JLabel("Applied Forces:"));
		myPanel.addWidget("    IAP", this, "forceByIAP");
		myPanel.addWidget("    Validation Force", this, "validationForce");
		myPanel.addWidget("    Crate Force", this, "crateForce");
		myPanel.addWidget("    Far Crate Force", this, "farCrateForce");

		myPanel.addWidget(new JSeparator());
		myPanel.addWidget(new JLabel("Muscle visibility flags:"));
		for (int i = 0; i < includedMuscleGroupNames.size(); i++) {
			String name = includedMuscleGroupNames.get(i);
			JCheckBox check = new JCheckBox("    " + name);
			check.setActionCommand(name + "Visible");
			check.setSelected(muscleVisible[i]);
			check.addItemListener(this);
			myPanel.addWidget(check);
		}

		myPanel.addWidget(new JSeparator());
		myPanel.addWidget(new JLabel("Muscle Excitations: "));

		for (int i = 0; i < mex.size(); i++) {
			String name = mex.get(i).getName();
			myPanel.addWidget("   " + name, mex.get(i), "excitation");
		}

		addControlPanel(myPanel);
	}

	// ********************************************************
	// ******************* Adding Controllers *****************
	// ********************************************************
	@Override
	public void addController(Controller controller, Model model) {
		super.addController(controller, model);
		if (controller instanceof PullController) {
			((PullController) controller).setStiffness(500);
		}
	}

	public void addHeadNeckWeight() {
		PointForceController pfc = new PointForceController(
				addPointForce("HeadNeck_centroid", new Vector3d(0, 1, 0), 0), new HeadNeckWeight());
		addController(pfc);

	}

	public void addIntraAbdominalPressure() {
		// Intra Abdominal Pressure
		BRerfPointFController pfc = new BRerfPointFController("thorax",
				addPointForce("IAP", new Vector3d(Math.sin(Math.PI / 6), Math.cos(Math.PI / 6), 0), 0),
				new AbdominalPressureForce());
		addController(pfc);
	}

	public void addValidationForce() {
		PointForceController pfc = new PointForceController(addPointForce("T3_centroid", new Vector3d(-1, 0, 0), 0),
				new ValidationForce());
		addController(pfc);

		pfc = new PointForceController(addPointForce("crate", new Vector3d(0, 1, 0), 0), new CrateForce(crateForce));
		addController(pfc);

		pfc = new PointForceController(addPointForce("farCrate", new Vector3d(0, 1, 0), 0),
				new FarCrateForce(farCrateForce));
		addController(pfc);
	}

	public PointForce addPointForce(String frameMarkerName, Vector3d direction, double magnitude) {
		FrameMarker m = mech.frameMarkers().get(frameMarkerName);
		PointForce pf = new PointForce(direction, m);
		pf.setMagnitude(magnitude);
		pf.setAxisLength(0.1);
		pf.setForceScaling(1000.0);

		RenderProps.setPointStyle(m, PointStyle.SPHERE);
		RenderProps.setPointColor(m, Color.cyan);
		RenderProps.setPointRadius(m, 0.002);

		RenderProps.setLineStyle(pf, LineStyle.CYLINDER);
		RenderProps.setLineRadius(pf, pf.getAxisLength() / 50);
		RenderProps.setLineColor(pf, Color.pink);

		mech.addForceEffector(pf);
		return pf;
	}

	// HeadNeckWeight
	public class HeadNeckWeight extends Function1x3 {

		public HeadNeckWeight() {
		}

		@Override
		public Point3d eval(double x) {
			Point3d p = new Point3d(0, -0.055, 0); //
			return p;
		}
	}

	// Force Functions
	public class AbdominalPressureForce extends Function1x3 {

		public AbdominalPressureForce() {
		}

		@Override
		public Point3d eval(double x) {
			Point3d p = new Point3d(-forceByIAP * Math.sin(Math.PI / 12), forceByIAP * Math.cos(Math.PI / 12), 0);
			return p;
		}
	}

	// Validation Force
	public class ValidationForce extends Function1x3 {

		public ValidationForce() {
		}

		@Override
		public Point3d eval(double x) {
			Point3d p = new Point3d(validationForce, 0, 0);
			return p;
		}
	}

	// addCrate
	public class CrateForce extends Function1x3 {

		public CrateForce(double F) {
		}

		@Override
		public Point3d eval(double x) {
			Point3d p = new Point3d(0, -crateForce, 0);
			return p;
		}
	}

	// addFarCrate
	public class FarCrateForce extends Function1x3 {

		public FarCrateForce(double F) {
		}

		@Override
		public Point3d eval(double x) {
			Point3d p = new Point3d(0, -farCrateForce, 0);
			return p;
		}
	}

	// PFController
	public class BRerfPointFController extends ControllerBase {

		private PointForce pf;
		private Function1x3 myFunction;
		private String rbname;

		public BRerfPointFController(String rbname, PointForce pointForce, Function1x3 func) {
			pf = pointForce;
			myFunction = func;
			this.rbname = rbname;
		}

		@Override
		public void apply(double t0, double t1) {

			RigidTransform3d pose = new RigidTransform3d(mech.rigidBodies().get(rbname).getPose());
			Point3d f = new Point3d(myFunction.eval(t0));
			f.transform(pose.R);
			pf.setForce(f);
		}

	}

	// ********************************************************
	// ******************* Adding Monitors ********************
	// ********************************************************
	public void addMonitors() {
		// added by amirabdi
		for (FrameMarker fm : mech.frameMarkers()) {
			FrameMarkerMonitor mon = new FrameMarkerMonitor(fm);
			mon.setName(fm.getName() + "_monitor");
			addMonitor(mon);
		}

		for (RigidBody rb : mech.rigidBodies()) {
			RigidBodyMonitor mon = new RigidBodyMonitor(rb);
			mon.setName(rb.getName() + "_monitor");
			addMonitor(mon);
		}

		for (AxialSpring as : mech.axialSprings()) {
			if (as instanceof Muscle) {
				MuscleMonitor mon = new MuscleMonitor((Muscle) as);
				mon.setName(as.getName() + "_monitor");
				addMonitor(mon);
			}
		}

		for (MultiPointSpring mps : mech.multiPointSprings()) {
			if (mps instanceof MultiPointSpring) {
				MultiPointMuscleMonitor mon = new MultiPointMuscleMonitor((MultiPointMuscle) mps);
				mon.setName(mps.getName() + "_monitor");
				addMonitor(mon);
			}
		}

		for (MuscleExciter mex : this.mex) {
			MuscleExciterMonitor mon = new MuscleExciterMonitor(mex);
			mon.setName(mex.getName() + "_ex_monitor");
			addMonitor(mon);
		}
	}

	// ********************************************************
	// ******************* StepAdjustment Advance *************
	// ********************************************************
	@Override
	public StepAdjustment advance(double t0, double t1, int flags) {

		StepAdjustment ret = super.advance(t0, t1, flags);
		double tStart = 0;
		double tEnd = 60;

		if (t0 == tStart) {
			for (Monitor mon : getMonitors()) {

				if (mon instanceof RigidBodyMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/RigidBodyMonitor/" + mon.getName() + ".m");
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((RigidBodyMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName + "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof FrameMarkerMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/FrameMarkerMonitor/" + mon.getName() + ".m");
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((FrameMarkerMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName + "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof TargetPointMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/TargetPointMonitor/" + mon.getName() + ".m");
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((TargetPointMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName + "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof MuscleMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/MuscleMonitor/" + mon.getName() + ".m");
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((MuscleMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName + "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof MultiPointMuscleMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/MultiPointMuscleMonitor/" + mon.getName() + ".m");
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((MultiPointMuscleMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName + "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof MuscleExciterMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/MuscleExciterMonitor/" + mon.getName() + ".m");
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((MuscleExciterMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName + "' (" + e.getMessage() + ")");
					}
				}
			}
		} else if (t0 == tEnd) {
			for (Monitor mon : getMonitors()) {
				if (mon instanceof RigidBodyMonitor) {
					((RigidBodyMonitor) mon).closeFile();
				}
				if (mon instanceof FrameMarkerMonitor) {
					((FrameMarkerMonitor) mon).closeFile();
				}
				if (mon instanceof MuscleMonitor) {
					((MuscleMonitor) mon).closeFile();
				}
				if (mon instanceof MultiPointMuscleMonitor) {
					((MultiPointMuscleMonitor) mon).closeFile();
				}
				if (mon instanceof MuscleExciterMonitor) {
					((MuscleExciterMonitor) mon).closeFile();
				}
			}
		}
		return ret;
	}

	// ********************************************************
	// ******************* Other Methods **********************
	// ********************************************************

	public void addRigidBodies() {
		addBone("L1", "lumbar1");
		addBone("L2", "lumbar2");
		addBone("L3", "lumbar3");
		addBone("L4", "lumbar4");
		addBone("L5", "lumbar5");
		addBone("sacrum", "sacrum");
		addBone("pelvis_rv", "pelvis_rv");
		addBone("pelvis_lv", "pelvis_lv");
		addBone("r_humerus", "arm_r_humerus");
		addBone("l_humerus", "humerus_lv");

		// Grouping the vertebrae
		thorax.add("ribcage");
		for (int i = 1; i <= 12; i++) {
			thorax.add("T" + Integer.toString(i));
		}

		for (int i = 1; i <= 5; i++) {
			lumbar.add("L" + Integer.toString(i));
		}

		pelvis.add("pelvis_rv");
		pelvis.add("pelvis_lv");

		// Transformation: scaling the size of all vertebrae
		AffineTransform3d trans = new AffineTransform3d();
		trans.applyScaling(.87, .87, .87);

		// composite
		RigidBody rcb = mech.rigidBodies().get("thorax");

		rcb.transformGeometry(trans);

		for (String str : lumbar) {
			RigidBody rb = mech.rigidBodies().get(str);
			rb.transformGeometry(trans);
		}

		trans = new AffineTransform3d();
		trans.applyScaling(.98, .98, .98);
		RigidBody rb = mech.rigidBodies().get("r_humerus");
		rb.transformGeometry(trans);
		rb = mech.rigidBodies().get("l_humerus");
		rb.transformGeometry(trans);

		// composite
		rcb = mech.rigidBodies().get("thorax");
		rcb.transformGeometry(new RigidTransform3d(-0.104, 1.0412, 0, 0, 0, 0));

		rb = mech.rigidBodies().get("L1");
		rb.transformGeometry(new RigidTransform3d(-0.1065, 1.1302, 0, 0, 0, 0));
		rb.setInertia(1.677, 0.01113, 0.01753, 0.0064);
		rb.setCenterOfMass(new Point3d(0.03274, 0.01940727, 0.00132891));

		rb = mech.rigidBodies().get("L2");
		rb.transformGeometry(new RigidTransform3d(-0.0951, 1.1014, 0, 0, 0, 0));
		rb.setInertia(1.689, 0.01091, 0.01682, 0.00591);
		rb.setCenterOfMass(new Point3d(0.02524622, 0.02021242, 0));

		rb = mech.rigidBodies().get("L3");
		rb.transformGeometry(new RigidTransform3d(-0.0884, 1.0663, 0, 0, 0, 0));
		rb.setInertia(1.67, 0.01066, 0.01608, 0.00541);
		rb.setCenterOfMass(new Point3d(0.017839, 0.02327394, 0));

		rb = mech.rigidBodies().get("L4");
		rb.transformGeometry(new RigidTransform3d(-0.089, 1.0331, 0, 0, 0, 0));
		rb.setInertia(1.799, 0.01123, 0.01643, 0.0052);
		rb.setCenterOfMass(new Point3d(0.01816, 0.02179527, 0));

		rb = mech.rigidBodies().get("L5");
		rb.transformGeometry(new RigidTransform3d(-0.098, 1.001, 0, 0, 0, 0));
		rb.setInertia(1.824, 0.01219, 0.01765, 0.00546);
		rb.setCenterOfMass(new Point3d(0.01728, 0.0183, 0));

		rb = mech.rigidBodies().get("sacrum");
		rb.transformGeometry(new RigidTransform3d(0, 0.93, 0, 0, 0, 0));
		rb.setInertia(7.486, 0.075, 0.08, 0.03);

		rb = mech.rigidBodies().get("r_humerus");
		rb.transformGeometry(new RigidTransform3d(-0.08049, 1.38654, 0.16907, 0, 0, 0));
		rb.setDensity(14000 * 1.814);

		rb = mech.rigidBodies().get("l_humerus");
		rb.transformGeometry(new RigidTransform3d(-0.08049, 1.38654, -0.16907, 0, 0, 0));
		rb.setDensity(18570 * 1.814);

		for (String st : pelvis) {
			rb = mech.rigidBodies().get(st);
			rb.transformGeometry(new RigidTransform3d(0, 0.979, 0, 0, 0, 0));
		}

		// rb = mech.rigidBodies ().get ("ribcage");
		rb = mech.rigidBodies().get("thorax");
		rb.setInertia(18.619, 0.165, 0.15, 0.125);
		rb.setCenterOfMass(new Point3d(0.02244, 0.20665, 0));

		// RenderProperties
		boneRenderProps = new RenderProps();
		boneRenderProps.setFaceColor(new Color(238, 232, 170));
		boneRenderProps.setAlpha(1);
		boneRenderProps.setShading(Shading.SMOOTH);
		for (RigidBody rbb : mech.rigidBodies()) {
			rbb.setRenderProps(boneRenderProps);
		}
	}

	protected String getGeometriesPath() {
		return getMeshesPath();
	}

	protected String getMeshesPath() {		
		return ArtisynthPath.getSrcRelativePath(LumbarSpineBaseDemo.class, "geometry/");
	}

	public void addBone(String name, String fileName) {

		String rigidBodyPath = getGeometriesPath();
		RigidBody rb = new RigidBody(name);
		PolygonalMesh mesh = null;
		try {
			mesh = new PolygonalMesh(new File(rigidBodyPath + fileName + ".obj"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		rb.setSurfaceMesh(mesh, null);
		rb.setDensity(1500); // Because we have already assigned the mass
		// rb.setDensity (0);
		mech.addRigidBody(rb);
	}

	public void makeThorax() {

		RigidBody rcb = new RigidBody("thorax");
		addMesh(rcb, "ribcage", "ribcage_s");
		addMesh(rcb, "T1", "thoracic1_s");
		addMesh(rcb, "T2", "thoracic2_s");
		addMesh(rcb, "T3", "thoracic3_s");
		addMesh(rcb, "T4", "thoracic4_s");
		addMesh(rcb, "T5", "thoracic5_s");
		addMesh(rcb, "T6", "thoracic6_s");
		addMesh(rcb, "T7", "thoracic7_s");
		addMesh(rcb, "T8", "thoracic8_s");
		addMesh(rcb, "T9", "thoracic9_s");
		addMesh(rcb, "T10", "thoracic10_s");
		addMesh(rcb, "T11", "thoracic11_s");
		addMesh(rcb, "T12", "thoracic12_s");
		mech.addRigidBody(rcb);
	}

	public void addMesh(RigidBody rcb, String name, String fileName) {

		String rigidBodyPath = getGeometriesPath();
		RigidMeshComp mc = new RigidMeshComp(name);
		PolygonalMesh mesh = null;
		try {
			mesh = new PolygonalMesh(new File(rigidBodyPath + fileName + ".obj"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		mc.setMesh(mesh);
		rcb.addMeshComp(mc);
	}

	public void fmTransformation(String rbName, Vector3d centroid) {
		for (FrameMarker fm : mech.frameMarkers()) {
			if (rbName.equals(fm.getFrame().getName())) {
				fm.transformGeometry(new RigidTransform3d(centroid, AxisAngle.IDENTITY));
			}
		}
	}

	public void inertiaTransformation(String rbName, Vector3d centroid) {
		if ("thorax".equals(rbName)) {
			RigidBody rcb = mech.rigidBodies().get(rbName);
			rcb.setCenterOfMass(new Point3d(0, 0, 0));
		} else {
			RigidBody rb = mech.rigidBodies().get(rbName);
			rb.setCenterOfMass(new Point3d(0, 0, 0));
		}

	}

	// The version for transfering everything to the center of mass instead of
	// centroid.
	public void transformRigidBodiesToCOM() {
		Vector3d COM = new Vector3d();
		PolygonalMesh mesh;

		for (RigidBody rb : mech.rigidBodies()) {
			COM = new Vector3d(rb.getCenterOfMass().x, rb.getCenterOfMass().y, rb.getCenterOfMass().z);

			if (rb.getName().equals("thorax")) {
				RigidBody rcb = mech.rigidBodies().get("thorax");
				MeshComponentList<RigidMeshComp> rcbMeshes = rcb.getMeshComps();
				COM.negate();

				// Translating all meshes so that thorax_centroid is set as the
				// Origin of
				// thorax frame ()
				for (String str : thorax) {
					MeshComponent mc = rcbMeshes.get(str);
					mesh = (PolygonalMesh) mc.getMesh();
					mesh.transform(new RigidTransform3d(COM, AxisAngle.IDENTITY));
				}
				fmTransformation(rcb.getName(), COM);
				inertiaTransformation(rcb.getName(), COM);

				// Translating the origin thorax frame so that its
				// World-coordinate
				// is set to original world coordinate of thorax-centroid
				COM.negate();
				rcb.transformGeometry(new RigidTransform3d(COM, AxisAngle.IDENTITY));
			} else {
				mesh = new PolygonalMesh();
				mesh = rb.getSurfaceMesh();
				COM.negate();
				mesh.transform(new RigidTransform3d(COM, AxisAngle.IDENTITY));
				fmTransformation(rb.getName(), COM);
				inertiaTransformation(rb.getName(), COM);
				COM.negate();
				rb.transformGeometry(new RigidTransform3d(COM, AxisAngle.IDENTITY));
			}
		}
	}

	public void addAbdomen() {
		// ******************* Creating abdomen*************************
		RigidBody rb;

		Matrix3d m;
		Vector3d p;
		RigidTransform3d rt;
		AffineTransform3d affTrans;
		// Creates a semi-circle
		rt = new RigidTransform3d();
		rt.setTranslation(new Vector3d(-1, 0, 0));

		PolygonalMesh mesh = null;
		String abdomenMeshPath = getGeometriesPath() + "abdomen.obj";

		boolean constructMeshUsingCSG = false;
		if (constructMeshUsingCSG) {
			PolygonalMesh mesh1, mesh2;
			mesh1 = MeshFactory.createOctahedralSphere(1, 3);
			mesh2 = MeshFactory.createBox(2, 2, 2);
			mesh2.transform(rt);
			mesh = MeshFactory.getSubtraction(mesh1, mesh2);
			try {
				mesh.write(new File(abdomenMeshPath), "%g", true);
			} catch (Exception e) {
			}
		} else {
			try {
				mesh = new PolygonalMesh(abdomenMeshPath);
			} catch (Exception e) {
				System.out.println("ERROR reading mesh file " + abdomenMeshPath + ":");
				System.out.println(e.getMessage());
			}
		}

		// Transforms the semi-circle to a semi-ellipsoid
		m = new Matrix3d(0.01, 0, 0, 0, 0.08, 0, 0, 0, 0.05);
		p = new Vector3d(0, 0, 0);

		affTrans = new AffineTransform3d(m, p);
		mesh.transform(affTrans);

		rb = new RigidBody("abdomen");
		rb.setSurfaceMesh(mesh, null);
		rb.setPosition(new Point3d(0, 0, 0));
		// rb.setMass(0);
		AffineTransform3d trans = new AffineTransform3d();
		rb.transformGeometry(trans);
		rb.transformGeometry(new RigidTransform3d(-0.008, 1.091, 0, 0, 0, 0));
		RenderProps.setFaceColor(rb, Color.white);
		mech.addRigidBody(rb);
	}

	public void addFrameMarkers() {

		fmRenderPropsMuscle = new RenderProps();
		fmRenderPropsMuscle.setPointStyle(PointStyle.SPHERE);
		fmRenderPropsMuscle.setPointRadius(0.0042);
		fmRenderPropsMuscle.setPointColor(Color.RED);
		fmRenderPropsMuscle.setAlpha(0.0);

		fmRenderPropsCOM = new RenderProps();
		fmRenderPropsCOM.setPointStyle(PointStyle.SPHERE);
		fmRenderPropsCOM.setPointRadius(0.008);
		fmRenderPropsCOM.setPointColor(Color.BLUE);
		fmRenderPropsCOM.setAlpha(0.0);

		// Adding Muscle Frame Makers
		FrameMarker fm;
		boolean ok = true;
		int i = 0;

		for (MuscleInfo mInfo : allMusclesInfo) {
			for (MusclePoint mp : mInfo.points) {

				if (ok) {
					fm = new FrameMarker(mech.rigidBodies().get(mp.body), mp.pnt);
					fm.setName(mInfo.name + "_fm" + Integer.toString(i));
					i++;
					fm.setRenderProps(fmRenderPropsMuscle);
					mech.addFrameMarker(fm);
				}
			}
			i = 0;
		}

		// Adding FrameMarkers for Rigid Bodies' COM
		for (RigidBody rb : mech.rigidBodies()) {
			if (rb.getName().equals("thorax")) {
				// Currently we are assuming the the centroids of the ribcage
				// and thorax are the same.
				RigidBody rcb = mech.rigidBodies().get("thorax");
				MeshComponentList<RigidMeshComp> rcbMeshes = rcb.getMeshComps();

				for (String str : thorax) {
					MeshComponent mc = rcbMeshes.get(str);
					Point3d com = new Point3d();
					mc.getMesh().computeCentroid(com);
					fm = new FrameMarker(rb, com);
					fm.setName(mc.getName() + "_centroid");
					bodyMassCenters.add(fm.getName());
					mech.addFrameMarker(fm);
					fm.setRenderProps(fmRenderPropsCOM);
				}

				Point3d com = new Point3d(mech.frameMarkers().get("ribcage_centroid").getLocation());
				fm = new FrameMarker(rb, com);
				fm.setName(rb.getName() + "_centroid");
				bodyMassCenters.add(fm.getName());
				mech.addFrameMarker(fm);
				fm.setRenderProps(fmRenderPropsCOM);
			} else {
				Point3d com = new Point3d();
				rb.getSurfaceMesh().computeCentroid(com);
				// com.inverseTransform(rb.getPose());
				fm = new FrameMarker(rb, com);
				// fm = new FrameMarker (rb, rb.getCenterOfMass ());
				fm.setName(rb.getName() + "_centroid");
				bodyMassCenters.add(fm.getName());
				mech.addFrameMarker(fm);
				fm.setRenderProps(fmRenderPropsCOM);
			}
		}

		// FrameMarker for Head and Neck Weight
		Point3d T1_centroid = new Point3d(mech.frameMarkers().get("T1_centroid").getLocation());
		fm = new FrameMarker(mech.rigidBodies().get("thorax"),
				new Point3d(T1_centroid.x + 0.06, T1_centroid.y + 0.137, T1_centroid.z + 0));
		fm.setName("HeadNeck_centroid");
		fm.setRenderProps(fmRenderPropsCOM);
		mech.addFrameMarker(fm);

		// FrameMarker for IAP
		Point3d T12_centroid = new Point3d(mech.frameMarkers().get("T12_centroid").getLocation());
		fm = new FrameMarker(mech.rigidBodies().get("thorax"),
				new Point3d(T12_centroid.x + 0.08, T12_centroid.y + 0, T12_centroid.z + 0));
		fm.setName("IAP");
		fm.setRenderProps(fmRenderPropsCOM);
		mech.addFrameMarker(fm);

		fm = new FrameMarker(mech.rigidBodies().get("thorax"),
				new Point3d(T12_centroid.x + 0.29, T12_centroid.y + 0, T12_centroid.z + 0));
		fm.setName("crate");
		fm.setRenderProps(fmRenderPropsCOM);
		mech.addFrameMarker(fm);

		fm = new FrameMarker(mech.rigidBodies().get("thorax"),
				new Point3d(T12_centroid.x + 0.29 + 0.30, T12_centroid.y + 0, T12_centroid.z + 0));
		fm.setName("farCrate");
		fm.setRenderProps(fmRenderPropsCOM);
		mech.addFrameMarker(fm);

		// FrameMarker for Offset Motion Targets
		Point3d ribcage_centroid = new Point3d(mech.frameMarkers().get("ribcage_centroid").getLocation());
		fm = new FrameMarker(mech.rigidBodies().get("thorax"),
				new Point3d(ribcage_centroid.x, ribcage_centroid.y + 0, ribcage_centroid.z + 0.10));
		fm.setName("ribcageMotionTarget_r");
		fm.setRenderProps(fmRenderPropsCOM);
		mech.addFrameMarker(fm);

		fm = new FrameMarker(mech.rigidBodies().get("thorax"),
				new Point3d(ribcage_centroid.x, ribcage_centroid.y + 0, ribcage_centroid.z - 0.10));
		fm.setName("ribcageMotionTarget_l");
		fm.setRenderProps(fmRenderPropsCOM);
		mech.addFrameMarker(fm);

		// FrameMarkers for FrameSprings
		fm = new FrameMarker();
		fm.setName("L5_S1_IVDjnt_A");
		fm.setFrame(mech.rigidBodies().get("sacrum"));
		fm.setLocation(new Point3d(-0.102, 0.05600000000000005, 0.0));
		mech.addFrameMarker(fm);

		fm = new FrameMarker();
		fm.setName("L4_L5_IVDjnt_A");
		fm.setFrame(mech.rigidBodies().get("L5"));
		fm.setLocation(new Point3d(0.009498444198113215, 0.017414155377358576, -6.423386455857517E-19));
		mech.addFrameMarker(fm);

		fm = new FrameMarker();
		fm.setName("L3_L4_IVDjnt_A");
		fm.setFrame(mech.rigidBodies().get("L4"));
		fm.setLocation(new Point3d(0.002644404952830179, 0.019190267122641602, -6.423386455857517E-19));
		mech.addFrameMarker(fm);

		fm = new FrameMarker();
		fm.setName("L2_L3_IVD_jnt_A");
		fm.setFrame(mech.rigidBodies().get("L3"));
		fm.setLocation(new Point3d(-0.002899711084905665, 0.018892314622641626, -2.9457568459983515E-19));

		mech.addFrameMarker(fm);

		fm = new FrameMarker();
		fm.setName("L1_L2_IVD_jnt_A");
		fm.setFrame(mech.rigidBodies().get("L2"));
		fm.setLocation(new Point3d(-0.004038857735849055, 0.014631359622641504, 1.5547050020546855E-19));

		mech.addFrameMarker(fm);

		fm = new FrameMarker();
		fm.setName("Torsojnt_A");
		fm.setFrame(mech.rigidBodies().get("L1"));
		fm.setLocation(new Point3d(-0.00456781773584905, 0.016483901509434018, 1.5547050020546855E-19));
		mech.addFrameMarker(fm);

		fm = new FrameMarker();
		fm.setName("Torsojnt_B");
		// fm.setFrame (mech.rigidBodies ().get ("ribcage"));
		fm.setFrame(mech.rigidBodies().get("thorax"));
		fm.setLocation(new Point3d(-0.020287463301886785, 0.1352094431603772, 6.032547169813912E-7));
		mech.addFrameMarker(fm);

		// adding Frame Marker for Abdjnt

		fm = new FrameMarker();
		fm.setName("Abdjnt_A");
		fm.setFrame(mech.rigidBodies().get("abdomen"));
		fm.setLocation(new Point3d(-0.09, -0.09000000000000008, 0.0));
		mech.addFrameMarker(fm);

		fm = new FrameMarker();
		fm.setName("Abdjnt_B");
		fm.setFrame(mech.rigidBodies().get("sacrum"));
		fm.setLocation(new Point3d(-0.098, 0.07099999999999984, 0.0));
		mech.addFrameMarker(fm);
	}

	public void makeCOMfm(RigidBody rb, Point3d com) {
		FrameMarker fm;
		fm = new FrameMarker(rb, com);
		fm.setName(rb.getName() + "_COM");
		bodyMassCenters.add(fm.getName());
		mech.addFrameMarker(fm);
		fm.setRenderProps(fmRenderPropsCOM);
	}

	public void addAdditionalFrameMarkers() {
	}

	/**
	 * Reads muscles information from the musculoskeletal model of lumbar spine by
	 * Christophy et al. (2012) inside OpenSim.
	 */
	public void loadMusclesInfo() {

		allMusclesInfo = null;
		try {
			allMusclesInfo = MuscleParser.parse(getMeshesPath() + "ChristophyMuscle.txt");
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		for (MuscleInfo mInfo : allMusclesInfo) {
			for (MusclePoint mp : mInfo.points) {
				// if ("torso".equals (mp.body))
				// mp.body = "ribcage";
				if ("torso".equals(mp.body))
					mp.body = "thorax";
				else if ("pelvis".equals(mp.body))
					mp.body = "pelvis_rv";
				else if ("lumbar1".equals(mp.body))
					mp.body = "L1";
				else if ("lumbar2".equals(mp.body))
					mp.body = "L2";
				else if ("lumbar3".equals(mp.body))
					mp.body = "L3";
				else if ("lumbar4".equals(mp.body))
					mp.body = "L4";
				else if ("lumbar5".equals(mp.body))
					mp.body = "L5";
				else if ("Abdomen".equals(mp.body))
					mp.body = "abdomen";
			}
		}
	}

	public void makeMuscles() {

		Muscle m;
		MultiPointMuscle mpm;
		int i = 0;
		int j = 0;
		int k = 0;

		for (MuscleInfo mInfo : allMusclesInfo) {
			if (mInfo.points.size() > 2) {
				mpm = new MultiPointMuscle(mInfo.name);
				i = 0;
				j = 0;
				for (MusclePoint mp : mInfo.points) {
					k = 0;
					for (String rbName : muscleInjuryLevels) {
						if (rbName.equals(mp.body)) {
							if (!mInfo.group.equals("Psoas_major_r") && !mInfo.group.equals("Psoas_major_l")) {
								k++;
							}
						}
					}
					if (k == 0) { // This checks if point mp is not on the
									// injured
									// vertebral level so that to be removed
						mpm.addPoint(mech.frameMarkers().get((mInfo.name + "_fm" + Integer.toString(i))));
						j++;
					}
					i++;
				}

				if (j > 1) {
					mpm.setMaterial(getMuscleMaterial(mInfo));
					mpm.setRestLength(mpm.getLength());
					allMultiPointMuscles.add(mpm);
				}
			} else if (mech.frameMarkers().get((mInfo.name + "_fm0")) != null) {
				m = new Muscle(mInfo.name);
				k = 0;
				for (MusclePoint mp : mInfo.points) {
					for (String rbName : muscleInjuryLevels) {
						if (rbName.equals(mp.body)) {
							if (!mInfo.group.equals("Psoas_major_r") && !mInfo.group.equals("Psoas_major_l")) {
								k++;
							}
						}
					}
				}
				if (k == 0) {
					m.setPoints(mech.frameMarkers().get((mInfo.name + "_fm0")),
							mech.frameMarkers().get((mInfo.name + "_fm1")));
					m.setMaterial(getMuscleMaterial(mInfo));
					m.setRestLength(m.getLength());
					allMuscles.add(m);
				}
			}
		}

		for (Muscle mus : allMuscles) {

			String str = mus.getName();
			String[] chars = str.split("");
			String muscleGeneralName = new String();
			for (i = 0; i < chars.length - 2; i++) {
				muscleGeneralName = muscleGeneralName + chars[i];
			}
			allMuscleGeneralNames.add(muscleGeneralName);

		}

		for (MultiPointMuscle mpmus : allMultiPointMuscles) {

			String str = mpmus.getName();
			String[] chars = str.split("");
			String multiPointMuscleGeneralName = new String();
			for (i = 0; i < chars.length - 2; i++) {
				multiPointMuscleGeneralName = multiPointMuscleGeneralName + chars[i];
			}
			allMultiPointMuscleGeneralNames.add(multiPointMuscleGeneralName);
		}
	}

	public void addMuscles() {
		addMuscleGroup("Rectus_Abdominis", "_r", Color.white);
		addMuscleGroup("Rectus_Abdominis", "_l", Color.white);
		addMuscleGroup("Int_Oblique", "_r", Color.white);
		addMuscleGroup("Int_Oblique", "_l", Color.white);
		addMuscleGroup("Ext_Oblique", "_r", Color.white);
		addMuscleGroup("Ext_Oblique", "_l", Color.white);
		addMuscleGroup("Multifidus", "_r", Color.white);
		addMuscleGroup("Multifidus", "_l", Color.white);
		addMuscleGroup("Psoas_major", "_r", Color.white);
		addMuscleGroup("Psoas_major", "_l", Color.white);
		addMuscleGroup("quadratus_lumborum", "_r", Color.white);
		addMuscleGroup("quadratus_lumborum", "_l", Color.white);
		addMuscleGroup("longissimus_thoracis_pars_lumborum", "_r", Color.white);
		addMuscleGroup("longissimus_thoracis_pars_lumborum", "_l", Color.white);
		addMuscleGroup("longissimus_thoracis_pars_thoracis", "_r", Color.white);
		addMuscleGroup("longissimus_thoracis_pars_thoracis", "_l", Color.white);
		addMuscleGroup("iliocostalis_lumborum_pars_lumborum", "_r", Color.white);
		addMuscleGroup("iliocostalis_lumborum_pars_lumborum", "_l", Color.white);
		addMuscleGroup("iliocostalis_lumborum_pars_thoracis", "_r", Color.white);
		addMuscleGroup("iliocostalis_lumborum_pars_thoracis", "_l", Color.white);

		// Visibility of all included Muscles inside Control Panel
		muscleVisible = new boolean[includedMuscleGroupNames.size()];
		for (int i = 0; i < includedMuscleGroupNames.size(); i++) {
			muscleVisible[i] = false;
		}

	}

	public void addMuscleGroup(String str, String side, Color color) {

		ArrayList<Muscle> gMuscle = new ArrayList<Muscle>();
		ArrayList<MultiPointMuscle> gMultiPointMuscle = new ArrayList<MultiPointMuscle>();

		for (MuscleInfo mInfo : allMusclesInfo) {

			if ((str + side).equals(mInfo.group)) {

				for (Muscle m : allMuscles) {
					if (m.getName().equals(mInfo.name)) {
						mech.addAxialSpring(m);
						gMuscle.add(m);

						if (includedMuscleGroupNames.size() < 1) {
							includedMuscleGroupNames.add(str);
						} else {
							if (!includedMuscleGroupNames.get(includedMuscleGroupNames.size() - 1).equals(str))
								includedMuscleGroupNames.add(str);
						}
					}
				}

				for (MultiPointMuscle mpm : allMultiPointMuscles) {
					if (mpm.getName().equals(mInfo.name)) {
						mech.addMultiPointSpring(mpm);
						gMultiPointMuscle.add(mpm);

						if (includedMuscleGroupNames.size() < 1) {
							includedMuscleGroupNames.add(str);
						} else {
							if (!includedMuscleGroupNames.get(includedMuscleGroupNames.size() - 1).equals(str))
								includedMuscleGroupNames.add(str);
						}
					}
				}
			}

		}

		// Render Properties of the muscle group
		RenderProps rp = new RenderProps();
		rp.setLineStyle(LineStyle.CYLINDER);
		rp.setLineColor(color);
		rp.setLineRadius(0.0025);

		for (Muscle m : gMuscle) {
			m.setExcitationColor(Color.red);
			m.setRenderProps(rp);
		}
		for (MultiPointMuscle mpm : gMultiPointMuscle) {
			mpm.setExcitationColor(Color.red);
			mpm.setRenderProps(rp);
		}

	}

	public void disableMuscle(String rbName) {

		for (MuscleInfo mInfo : allMusclesInfo) {

			if (mech.axialSprings().get(mInfo.name) != null) {
				for (MusclePoint mp : mInfo.points) {
					if (rbName.equals(mp.body)) {
						((Muscle) mech.axialSprings().get(mInfo.name)).setEnabled(false);
					}
				}
			}
		}
	}

	public void addExciters() {
		addIndividualMex();
	}

	// Groups right and left excitations together in one excitation
	public void addGroupedMex() {
		for (String str : includedMuscleGroupNames) {

			if (str == "Rectus_Abdominis") {
				MuscleExciter mRight = new MuscleExciter(str + "_r");
				MuscleExciter mLeft = new MuscleExciter(str + "_l");

				for (MuscleInfo mInfo : allMusclesInfo) {

					if (mech.axialSprings().get(mInfo.name) != null) {
						if (mInfo.group.equals(str + "_r")) {
							AxialSpring as = mech.axialSprings().get(mInfo.name);
							mRight.addTarget((Muscle) as, 1.0);
						}
						if (mInfo.group.equals(str + "_l")) {
							AxialSpring as = mech.axialSprings().get(mInfo.name);
							mLeft.addTarget((Muscle) as, 1.0);
						}
					} else if (mech.multiPointSprings().get(mInfo.name) != null) {
						if (mInfo.group.equals(str + "_r")) {
							MultiPointSpring mps = mech.multiPointSprings().get(mInfo.name);
							mRight.addTarget((MultiPointMuscle) mps, 1.0);
						}
						if (mInfo.group.equals(str + "_l")) {
							MultiPointSpring mps = mech.multiPointSprings().get(mInfo.name);
							mLeft.addTarget((MultiPointMuscle) mps, 1.0);
						}
					}

				}
				mex.add(mRight);
				mex.add(mLeft);
				mech.addMuscleExciter(mRight);
				mech.addMuscleExciter(mLeft);
			} else if (str == "iliocostalis_lumborum_pars_thoracis") {
				MuscleExciter mRight = new MuscleExciter(str + "_r");
				MuscleExciter mLeft = new MuscleExciter(str + "_l");

				for (MuscleInfo mInfo : allMusclesInfo) {

					if (mech.axialSprings().get(mInfo.name) != null) {
						if (mInfo.group.equals(str + "_r")) {
							AxialSpring as = mech.axialSprings().get(mInfo.name);
							mRight.addTarget((Muscle) as, 1.0);
						}
						if (mInfo.group.equals(str + "_l")) {
							AxialSpring as = mech.axialSprings().get(mInfo.name);
							mLeft.addTarget((Muscle) as, 1.0);
						}
					} else if (mech.multiPointSprings().get(mInfo.name) != null) {
						if (mInfo.group.equals(str + "_r")) {
							MultiPointSpring mps = mech.multiPointSprings().get(mInfo.name);
							mRight.addTarget((MultiPointMuscle) mps, 1.0);
						}
						if (mInfo.group.equals(str + "_l")) {
							MultiPointSpring mps = mech.multiPointSprings().get(mInfo.name);
							mLeft.addTarget((MultiPointMuscle) mps, 1.0);
						}
					}

				}
				mex.add(mRight);
				mex.add(mLeft);
				mech.addMuscleExciter(mRight);
				mech.addMuscleExciter(mLeft);
			} else if (str == "quadratus_lumborum") {
				MuscleExciter mRight = new MuscleExciter(str + "_r");
				MuscleExciter mLeft = new MuscleExciter(str + "_l");

				for (MuscleInfo mInfo : allMusclesInfo) {

					if (mech.axialSprings().get(mInfo.name) != null) {
						if (mInfo.group.equals(str + "_r")) {
							AxialSpring as = mech.axialSprings().get(mInfo.name);
							mRight.addTarget((Muscle) as, 1.0);
						}
						if (mInfo.group.equals(str + "_l")) {
							AxialSpring as = mech.axialSprings().get(mInfo.name);
							mLeft.addTarget((Muscle) as, 1.0);
						}
					} else if (mech.multiPointSprings().get(mInfo.name) != null) {
						if (mInfo.group.equals(str + "_r")) {
							MultiPointSpring mps = mech.multiPointSprings().get(mInfo.name);
							mRight.addTarget((MultiPointMuscle) mps, 1.0);
						}
						if (mInfo.group.equals(str + "_l")) {
							MultiPointSpring mps = mech.multiPointSprings().get(mInfo.name);
							mLeft.addTarget((MultiPointMuscle) mps, 1.0);
						}
					}

				}
				mex.add(mRight);
				mex.add(mLeft);
				mech.addMuscleExciter(mRight);
				mech.addMuscleExciter(mLeft);
			} else {
				MuscleExciter m = new MuscleExciter(str);

				for (MuscleInfo mInfo : allMusclesInfo) {

					if (mech.axialSprings().get(mInfo.name) != null) {
						if (mInfo.group.equals(str + "_r") || mInfo.group.equals(str + "_l")) {
							AxialSpring as = mech.axialSprings().get(mInfo.name);
							m.addTarget((Muscle) as, 1.0);
						}
					} else if (mech.multiPointSprings().get(mInfo.name) != null) {
						if (mInfo.group.equals(str + "_r") || mInfo.group.equals(str + "_l")) {
							MultiPointSpring mps = mech.multiPointSprings().get(mInfo.name);
							m.addTarget((MultiPointMuscle) mps, 1.0);
						}
					}

				}
				mex.add(m);
				mech.addMuscleExciter(m);
			}
		}
	}

	public void setInitialExcitation() {

		for (String str : includedMuscleGroupNames) {
			if (str == "Rectus_Abdominis") {
				Muscle as = (Muscle) mech.axialSprings().get("rect_abd_r");
				if (as != null) {
					as.setExcitation(0.079899);
				}
				as = (Muscle) mech.axialSprings().get("rect_abd_l");
				if (as != null) {
					as.setExcitation(0.003019);
				}
			} else if (str == "Multifidus") {
				excitationSetter("Multifidus", 0.133196);
			} else if (str == "Psoas_major") {
				excitationSetter("Psoas_major", 0.140366);
			} else if (str == "quadratus_lumborum") {
				excitationSetter("quadratus_lumborum", 0.059011);
			} else if (str == "longissimus_thoracis_pars_lumborum") {
				excitationSetter("longissimus_thoracis_pars_lumborum", 0.057680);
			} else if (str == "longissimus_thoracis_pars_thoracis") {
				excitationSetter("longissimus_thoracis_pars_thoracis", 0.340988);
			} else if (str == "iliocostalis_lumborum_pars_lumborum") {
				excitationSetter("iliocostalis_lumborum_pars_lumborum", 0.083122);
			} else if (str == "iliocostalis_lumborum_pars_thoracis") {
				excitationSetter("iliocostalis_lumborum_pars_thoracis", 0.182055);
			}

		}
	}

	public void excitationSetter(String str, double a) {
		for (MuscleInfo mInfo : allMusclesInfo) {

			if (mech.axialSprings().get(mInfo.name) != null) {
				if (mInfo.group.equals(str + "_r") || mInfo.group.equals(str + "_l")) {
					Muscle as = (Muscle) mech.axialSprings().get(mInfo.name);
					as.setExcitation(a);
				}
			} else if (mech.multiPointSprings().get(mInfo.name) != null) {
				if (mInfo.group.equals(str + "_r") || mInfo.group.equals(str + "_l")) {
					MultiPointMuscle mps = (MultiPointMuscle) mech.multiPointSprings().get(mInfo.name);
					mps.setExcitation(a);
				}
			}
		}
	}

	// Adds right and left excitations for each muscle group separately
	public void addSeparateRandLGroupedMex() {
		for (String str : includedMuscleGroupNames) {
			MuscleExciter mRight = new MuscleExciter(str + "_r");
			MuscleExciter mLeft = new MuscleExciter(str + "_l");

			for (MuscleInfo mInfo : allMusclesInfo) {

				if (mech.axialSprings().get(mInfo.name) != null) {
					if (mInfo.group.equals(str + "_r")) {
						AxialSpring as = mech.axialSprings().get(mInfo.name);
						mRight.addTarget((Muscle) as, 1.0);
					}
					if (mInfo.group.equals(str + "_l")) {
						AxialSpring as = mech.axialSprings().get(mInfo.name);
						mLeft.addTarget((Muscle) as, 1.0);
					}
				} else if (mech.multiPointSprings().get(mInfo.name) != null) {
					if (mInfo.group.equals(str + "_r")) {
						MultiPointSpring mps = mech.multiPointSprings().get(mInfo.name);
						mRight.addTarget((MultiPointMuscle) mps, 1.0);
					}
					if (mInfo.group.equals(str + "_l")) {
						MultiPointSpring mps = mech.multiPointSprings().get(mInfo.name);
						mLeft.addTarget((MultiPointMuscle) mps, 1.0);
					}
				}

			}
			mex.add(mRight);
			mex.add(mLeft);
			mech.addMuscleExciter(mRight);
			mech.addMuscleExciter(mLeft);
		}
	}

	public void addAllFasciclesIndividuallyMex() {

		for (AxialSpring as : mech.axialSprings()) {
			MuscleExciter m = new MuscleExciter(as.getName());
			m.addTarget((Muscle) as, 1.0);
			mex.add(m);
			mech.addMuscleExciter(m);
		}

		for (MultiPointSpring mps : mech.multiPointSprings()) {
			MuscleExciter m = new MuscleExciter(mps.getName());
			m.addTarget((MultiPointMuscle) mps, 1.0);
			mex.add(m);
			mech.addMuscleExciter(m);
		}

	}

	public void addMyFavoriteMex() {

		for (MuscleInfo mInfo : allMusclesInfo) {
			if ("Rectus_Abdominis_r".equals(mInfo.group) || "Rectus_Abdominis_l".equals(mInfo.group)
					|| "longissimus_thoracis_pars_lumborum_r".equals(mInfo.group)
					|| "longissimus_thoracis_pars_lumborum_l".equals(mInfo.group)
					|| "iliocostalis_lumborum_pars_thoracis_r".equals(mInfo.group)
					|| "iliocostalis_lumborum_pars_thoracis_l".equals(mInfo.group)) {
				if (mech.axialSprings().get(mInfo.name) != null) {
					MuscleExciter m = new MuscleExciter(mInfo.name);
					m.addTarget((Muscle) mech.axialSprings().get(mInfo.name), 1.0);
					mex.add(m);
					mech.addMuscleExciter(m);
				} else if (mech.multiPointSprings().get(mInfo.name) != null) {
					MuscleExciter m = new MuscleExciter(mInfo.name);
					m.addTarget((MultiPointMuscle) mech.multiPointSprings().get(mInfo.name), 1.0);
					mex.add(m);
					mech.addMuscleExciter(m);
				}
			}

			else {
				String str = mInfo.name;
				String[] chars = str.split("");
				String exciterName = new String();
				for (int i = 0; i < chars.length - 2; i++) {
					exciterName = exciterName + chars[i];
				}

				if (mech.axialSprings().get(mInfo.name) != null) {
					if (mInfo.name.equals(exciterName + "_l")) {
						MuscleExciter m = new MuscleExciter(exciterName);
						m.addTarget((Muscle) mech.axialSprings().get(mInfo.name), 1.0);
						if (mech.axialSprings().get(exciterName + "_r") != null) {
							m.addTarget((Muscle) mech.axialSprings().get(exciterName + "_r"), 1.0);
						}
						mex.add(m);
						mech.addMuscleExciter(m);
					} else if (mech.axialSprings().get(exciterName + "_l") == null) {
						MuscleExciter m = new MuscleExciter(exciterName);
						m.addTarget((Muscle) mech.axialSprings().get(mInfo.name), 1.0);
						mex.add(m);
						mech.addMuscleExciter(m);
					}

				} else if (mech.multiPointSprings().get(mInfo.name) != null) {
					if (mInfo.name.equals(exciterName + "_l")) {
						MuscleExciter m = new MuscleExciter(exciterName);
						m.addTarget((MultiPointMuscle) mech.multiPointSprings().get(mInfo.name), 1.0);
						if (mech.multiPointSprings().get(exciterName + "_r") != null) {
							m.addTarget((MultiPointMuscle) mech.multiPointSprings().get(exciterName + "_r"), 1.0);
						}
						mex.add(m);
						mech.addMuscleExciter(m);
					} else if (mech.multiPointSprings().get(exciterName + "_l") == null) {
						MuscleExciter m = new MuscleExciter(exciterName);
						m.addTarget((MultiPointMuscle) mech.multiPointSprings().get(mInfo.name), 1.0);
						mex.add(m);
						mech.addMuscleExciter(m);
					}
				}
			}

		}
	}

	public void addIndividualMex() {

		for (MuscleInfo mInfo : allMusclesInfo) {
			String exciterName = mInfo.name;

			if (mech.axialSprings().get(mInfo.name) != null) {
				MuscleExciter m = new MuscleExciter(exciterName);
				m.addTarget((Muscle) mech.axialSprings().get(mInfo.name), 1.0);
				mex.add(m);
				mech.addMuscleExciter(m);
			} else if (mech.multiPointSprings().get(mInfo.name) != null) {
				MuscleExciter m = new MuscleExciter(exciterName);
				m.addTarget((MultiPointMuscle) mech.multiPointSprings().get(mInfo.name), 1.0);
				mex.add(m);
				mech.addMuscleExciter(m);
			}
		}
	}

	private void updateVisibility() {

		for (int i = 0; i < includedMuscleGroupNames.size(); i++) {
			boolean seeMe = (muscleVisible[i]);

			for (MuscleInfo mInfo : allMusclesInfo) {

				if (mech.axialSprings().get(mInfo.name) != null) {
					if (mInfo.group.equals(includedMuscleGroupNames.get(i) + "_r")
							|| mInfo.group.equals(includedMuscleGroupNames.get(i) + "_l"))
						RenderProps.setVisible(mech.axialSprings().get(mInfo.name), seeMe);
				} else if (mech.multiPointSprings().get(mInfo.name) != null) {
					if (mInfo.group.equals(includedMuscleGroupNames.get(i) + "_r")
							|| mInfo.group.equals(includedMuscleGroupNames.get(i) + "_l"))
						RenderProps.setVisible(mech.multiPointSprings().get(mInfo.name), seeMe);
				}
			}
		}
	}

	public void setMuscleVisible(boolean visible, int idx) {
		muscleVisible[idx] = visible;
		updateVisibility();
	}

	public boolean getMuscleVisible(int idx) {
		return muscleVisible[idx];
	}

	public void setBoneAlpha(double alpha) {
		boneRenderProps.setAlpha(alpha);
		for (RigidBody rb : mech.rigidBodies()) {
			RenderProps.setAlpha(rb, alpha);
		}
		// updateVisibility ();
	}

	public double getBoneAlpha() {
		return boneRenderProps.getAlpha();
	}

	public void setFrameMarkerAlpha(double alpha) {
		fmRenderPropsMuscle.setAlpha(alpha);
		fmRenderPropsCOM.setAlpha(alpha);
		for (FrameMarker fm : mech.frameMarkers()) {
			// RenderProps.setAlpha (fm, alpha);
			fm.setRenderProps(fmRenderPropsMuscle);
		}
		for (RigidBody rb : mech.rigidBodies()) {
			mech.frameMarkers().get(rb.getName() + "_centroid").setRenderProps(fmRenderPropsCOM);
		}
		// updateVisibility ();
	}

	public double getFrameMarkerAlpha() {
		return fmRenderPropsMuscle.getAlpha();
	}

	public void setForceByIAP(double mag) {
		forceByIAP = mag;
	}

	public double getForceByIAP() {
		return forceByIAP;
	}

	public void setValidationForce(double mag) {
		validationForce = mag;
	}

	public double getValidationForce() {
		return validationForce;
	}

	public void setCrateForce(double mag) {
		crateForce = mag;
	}

	public double getCrateForce() {
		return crateForce;
	}

	public void setFarCrateForce(double mag) {
		farCrateForce = mag;
	}

	public double getFarCrateForce() {
		return farCrateForce;
	}

	public Wrench getTorsojntForces() {
		Wrench forces;
		forces = new Wrench(mech.frameSprings().get("Torsojnt").getSpringForce());
		return forces;
	}

	public Wrench getL1L2Forces() {
		Wrench forces;
		forces = new Wrench(mech.frameSprings().get("L1_L2_IVD_jnt").getSpringForce());
		return forces;
	}

	public Wrench getL2L3Forces() {
		Wrench forces;
		forces = new Wrench(mech.frameSprings().get("L2_L3_IVD_jnt").getSpringForce());
		return forces;
	}

	public Wrench getL3L4Forces() {
		Wrench forces;
		forces = new Wrench(mech.frameSprings().get("L3_L4_IVDjnt").getSpringForce());
		return forces;
	}

	public Wrench getL4L5Forces() {
		Wrench forces;
		forces = new Wrench(mech.frameSprings().get("L4_L5_IVDjnt").getSpringForce());
		return forces;
	}

	public Wrench getL5S1Forces() {
		Wrench forces;
		forces = new Wrench(mech.frameSprings().get("L5_S1_IVDjnt").getSpringForce());
		return forces;
	}

	protected MasoudMillardLAM getMuscleMaterial(MuscleInfo mInfo) {
		MasoudMillardLAM m = new MasoudMillardLAM();
		m.setOptLength(mInfo.optFiberLen);
		m.setMaxForce(mInfo.maxForce / 1000 * 100 / 46.6);
		m.setMyFMTratioLit(mInfo.fiberRatio);
		m.setPassiveFraction(1);
		for (int i = 0; i < 11; i++) {
			String str = muscleGroupNames[i];
			if ((str + "_r").equals(mInfo.group) || (str + "_l").equals(mInfo.group)) {
				m.setMySarcomereLenLit(sarcomereLengths[i]);
			}
		}
		// m.setMySarcomereLenLit(2.8);
		return m;
	}

	public void addFrameSprings() {

		FrameSpring fs;
		SolidJoint sj;
		RevoluteJoint rj;
		Point3d cv = new Point3d();
		RenderProps fsRenderProps = new RenderProps();
		fsRenderProps.setLineStyle(LineStyle.SPINDLE);
		fsRenderProps.setLineRadius(0.005);
		fsRenderProps.setLineColor(Color.green);
		Vector3d move = new Vector3d();
		// fsRenderProps.setAlpha (0.0);

		// OffsetLinearFrameMaterial mat =
		// new OffsetLinearFrameMaterial (500000, 600, 0, 0);
		// HanOfLiFrMa mat =
		// new HanOfLiFrMa (500000, 600, 0, 0);
		HeuerOffLinFM mat = new HeuerOffLinFM(500000, 600, 0, 0);
		mat.setStiffness(435000, 2420000, 523000);
		// mat.setStiffness (251000, 438000, 332000);

		fs = new FrameSpring("Hipjnt");
		fs.setFrameA(mech.rigidBodies().get("pelvis_rv"));
		fs.setAttachFrameA(new RigidTransform3d(cv.x + move.x, cv.y + move.y, cv.z + move.z));
		fs.setFrameB(mech.rigidBodies().get("sacrum"));
		fs.setAttachFrameB(new RigidTransform3d(cv.x + move.x, cv.y + move.y, cv.z + move.z));
		fs.setMaterial(mat);
		fs.setInitialT21();
		mech.addFrameSpring(fs);

		fs = new FrameSpring("L5_S1_IVDjnt");
		fs.setFrameA(mech.rigidBodies().get("sacrum"));
		cv = new Point3d(mech.frameMarkers().get("L5_S1_IVDjnt_A").getLocation());
		fs.setAttachFrameA(new RigidTransform3d(cv.x + move.x, cv.y + move.y, cv.z + move.z));

		fs.setFrameB(mech.rigidBodies().get("L5"));
		cv = new Point3d(mech.frameMarkers().get("L4_L5_IVDjnt_A").getLocation());
		fs.setAttachFrameB(new RigidTransform3d(cv.x + move.x, cv.y + move.y, cv.z + move.z));
		fs.setMaterial(mat);
		fs.setInitialT21();
		mech.addFrameSpring(fs);

		fs = new FrameSpring("L4_L5_IVDjnt");
		fs.setFrameA(mech.rigidBodies().get("L5"));
		cv = new Point3d(mech.frameMarkers().get("L4_L5_IVDjnt_A").getLocation());
		fs.setAttachFrameA(new RigidTransform3d(cv.x + move.x, cv.y + move.y, cv.z + move.z));
		fs.setFrameB(mech.rigidBodies().get("L4"));
		cv = new Point3d(mech.frameMarkers().get("L3_L4_IVDjnt_A").getLocation());
		fs.setAttachFrameB(new RigidTransform3d(cv.x + move.x, cv.y + move.y, cv.z + move.z));
		fs.setMaterial(mat);
		fs.setInitialT21();
		mech.addFrameSpring(fs);

		fs = new FrameSpring("L3_L4_IVDjnt");
		fs.setFrameA(mech.rigidBodies().get("L4"));
		cv = new Point3d(mech.frameMarkers().get("L3_L4_IVDjnt_A").getLocation());
		fs.setAttachFrameA(new RigidTransform3d(cv.x + move.x, cv.y + move.y, cv.z + move.z));
		fs.setFrameB(mech.rigidBodies().get("L3"));
		cv = new Point3d(mech.frameMarkers().get("L2_L3_IVD_jnt_A").getLocation());
		fs.setAttachFrameB(new RigidTransform3d(cv.x + move.x, cv.y + move.y, cv.z + move.z));
		fs.setMaterial(mat);
		fs.setInitialT21();
		mech.addFrameSpring(fs);

		fs = new FrameSpring("L2_L3_IVD_jnt");
		fs.setFrameA(mech.rigidBodies().get("L3"));
		cv = new Point3d(mech.frameMarkers().get("L2_L3_IVD_jnt_A").getLocation());
		fs.setAttachFrameA(new RigidTransform3d(cv.x + move.x, cv.y + move.y, cv.z + move.z));
		fs.setFrameB(mech.rigidBodies().get("L2"));
		cv = new Point3d(mech.frameMarkers().get("L1_L2_IVD_jnt_A").getLocation());
		fs.setAttachFrameB(new RigidTransform3d(cv.x + move.x, cv.y + move.y, cv.z + move.z));
		fs.setMaterial(mat);
		fs.setInitialT21();
		mech.addFrameSpring(fs);

		fs = new FrameSpring("L1_L2_IVD_jnt");
		fs.setFrameA(mech.rigidBodies().get("L2"));
		cv = new Point3d(mech.frameMarkers().get("L1_L2_IVD_jnt_A").getLocation());
		fs.setAttachFrameA(new RigidTransform3d(cv.x + move.x, cv.y + move.y, cv.z + move.z));
		fs.setFrameB(mech.rigidBodies().get("L1"));
		cv = new Point3d(mech.frameMarkers().get("Torsojnt_A").getLocation());
		fs.setAttachFrameB(new RigidTransform3d(cv.x + move.x, cv.y + move.y, cv.z + move.z));
		fs.setMaterial(mat);
		fs.setInitialT21();
		mech.addFrameSpring(fs);

		fs = new FrameSpring("Torsojnt");
		fs.setFrameA(mech.rigidBodies().get("L1"));
		cv = new Point3d(mech.frameMarkers().get("Torsojnt_A").getLocation());
		fs.setAttachFrameA(new RigidTransform3d(cv.x + move.x, cv.y + move.y, cv.z + move.z));

		fs.setFrameB(mech.rigidBodies().get("thorax"));
		cv = new Point3d(mech.frameMarkers().get("Torsojnt_B").getLocation());
		fs.setAttachFrameB(new RigidTransform3d(cv.x + move.x, cv.y + move.y, cv.z + move.z));
		fs.setMaterial(mat);
		fs.setInitialT21();
		mech.addFrameSpring(fs);

		for (FrameSpring fsr : mech.frameSprings())
			fsr.setRenderProps(fsRenderProps);
		mech.frameSprings().get("Hipjnt").setRenderProps(null);

		// Fixing the pelvis to the ground and setting abdomen's dynamic to
		// false
		mech.rigidBodies().get("sacrum").setDynamic(false);
		mech.rigidBodies().get("pelvis_rv").setDynamic(false);
		mech.rigidBodies().get("pelvis_lv").setDynamic(false);
		mech.rigidBodies().get("abdomen").setDynamic(false);

		// Adding Solid Joints
		sj = new SolidJoint(mech.rigidBodies().get("r_humerus"), mech.rigidBodies().get("thorax"));
		sj.setName("ribcage-r_humerus_jnt");
		mech.addBodyConnector(sj);
		sj = new SolidJoint(mech.rigidBodies().get("l_humerus"), mech.rigidBodies().get("thorax"));
		sj.setName("ribcage-l_humerus_jnt");
		mech.addBodyConnector(sj);

		// Adding Revolute Joints
		rj = new RevoluteJoint();
		rj.setName(("Abdjnt"));

		Point3d pntA = new Point3d(mech.frameMarkers().get("Abdjnt_A").getLocation());
		Point3d pntB = new Point3d(mech.frameMarkers().get("Abdjnt_B").getLocation());
		rj.setBodies(mech.rigidBodies().get("abdomen"), new RigidTransform3d(pntA.x, pntA.y, pntA.z),
				mech.rigidBodies().get("sacrum"), new RigidTransform3d(pntB.x, pntB.y, pntB.z));
		RenderProps.setVisible(rj, false);
		mech.addBodyConnector(rj);

	}

	public void addFixators() {

		// fixate ("T12", "L1", "T12_L1_Solidjnt");
		fixate("thorax", "L1", "T12_L1_Solidjnt");
		// fixate ("L1", "L2", "L1_L2_jnt");
		// fixate("L2", "L3", "L2_L3_Solidjnt");
		// fixate("L3", "L4", "L3_L4_Solidjnt");
		// fixate("L4", "L5", "L4_L5_Solidjnt");
		// fixate ("L5", "sacrum", "L5_S1_jnt");
	}

	public void fixate(String rb1, String rb2, String name) {
		SolidJoint sj = new SolidJoint(mech.rigidBodies().get(rb1), mech.rigidBodies().get(rb2));
		sj.setName(name);
		mech.addBodyConnector(sj);
	}

	public void doLevelCalculations() {
		doLevelCalculationFor(muscleGroupNames[0], LD);
		doAbdLevelCalculationFor(muscleGroupNames[1], RA);
		doAbdLevelCalculationFor(muscleGroupNames[2], IO);
		doAbdLevelCalculationFor(muscleGroupNames[3], EO);
		doLevelCalculationFor(muscleGroupNames[4], MF);
		doLevelCalculationFor(muscleGroupNames[5], Ps);
		doLevelCalculationFor(muscleGroupNames[6], QL);
		doLevelCalculationFor(muscleGroupNames[7], LTpL);
		doLevelCalculationFor(muscleGroupNames[8], LTpT);
		doLevelCalculationFor(muscleGroupNames[9], ILpL);
		doLevelCalculationFor(muscleGroupNames[10], ILpT);
	}

	public void doLevelCalculationFor(String mGroupName, ArrayList<ArrayList<String>> mGroup) {
		// mGroup.add (new ArrayList<String> ());

		for (FrameSpring fs : mech.frameSprings()) {
			// calculate midpoint of the FrameSpring
			double PCSA = 0;
			ArrayList<String> Level = new ArrayList<String>();
			Level.add(fs.getName());
			for (MuscleInfo mInfo : allMusclesInfo) {
				if ((mGroupName + "_r").equals(mInfo.group) || (mGroupName + "_l").equals(mInfo.group)) {
					Vector3d levelPoint = new Vector3d();
					Vector3d rostralEnd;
					Vector3d caudalEnd;
					rostralEnd = new Vector3d(
							mech.frameMarkers().get(fs.getFrameB().getName() + "_centroid").getPosition());
					caudalEnd = new Vector3d(
							mech.frameMarkers().get(fs.getFrameA().getName() + "_centroid").getPosition());
					// Two special cases
					// if ("ribcage".equals (fs.getFrameB ().getName ())) {
					if ("thorax".equals(fs.getFrameB().getName())) {

						rostralEnd = new Vector3d(mech.frameMarkers().get("T12_centroid").getPosition());
					}
					if ("sacrum".equals(fs.getFrameA().getName())) {
						caudalEnd.y = 0.99;
					}

					levelPoint.add(rostralEnd, caudalEnd);
					levelPoint.scale(0.5);

					// Find and save the name of the muscle fascicles that pass through this level
					// (midpoint)
					double sign = 0;
					Vector3d pnt1;
					Vector3d pnt2 = new Vector3d();
					for (AxialSpring as : mech.axialSprings()) {
						if (mInfo.name.equals(as.getName())) {
							pnt1 = new Vector3d(as.getFirstPoint().getPosition());
							pnt2 = new Vector3d(as.getSecondPoint().getPosition());
							sign = (pnt1.y - levelPoint.y) * (pnt2.y - levelPoint.y);
							if (sign < 0) {
								Level.add(as.getName());
								Muscle m = new Muscle();
								m = (Muscle) as;
								PCSA = PCSA + ((AxialMuscleMaterial) m.getMaterial()).getMaxForce();
							}
						}
					}
					for (MultiPointSpring mps : mech.multiPointSprings()) {
						if (mInfo.name.equals(mps.getName())) {
							pnt1 = new Vector3d(mps.getPoint(0).getPosition());
							pnt2 = new Vector3d(mps.getPoint(mps.numPoints() - 1).getPosition());
							sign = (pnt1.y - levelPoint.y) * (pnt2.y - levelPoint.y);
							if (sign < 0) {
								Level.add(mps.getName());
								MultiPointMuscle mpm = new MultiPointMuscle();
								mpm = (MultiPointMuscle) mps;
								PCSA = PCSA + ((AxialMuscleMaterial) mpm.getMaterial()).getMaxForce();
							}
						}
					}
				}
			}
			Level.add(String.valueOf(PCSA));
			mGroup.add(Level);
		}
		saveLevelCalculationsInText(mGroupName, mGroup);
	}

	public void doAbdLevelCalculationFor(String mGroupName, ArrayList<ArrayList<String>> mGroup) {

		for (FrameSpring fs : mech.frameSprings()) {
			double PCSA = 0;
			ArrayList<String> Level = new ArrayList<String>();
			Level.add(fs.getName());
			for (MuscleInfo mInfo : allMusclesInfo) {
				if ((mGroupName + "_r").equals(mInfo.group) || (mGroupName + "_l").equals(mInfo.group)) {
					for (AxialSpring as : mech.axialSprings()) {
						if (mInfo.name.equals(as.getName())) {
							Level.add(as.getName());

							PCSA = PCSA + ((AxialMuscleMaterial) as.getMaterial()).getMaxForce();
						}
					}
					for (MultiPointSpring mps : mech.multiPointSprings()) {
						if (mInfo.name.equals(mps.getName())) {
							Level.add(mps.getName());
							MultiPointMuscle mpm = new MultiPointMuscle();
							mpm = (MultiPointMuscle) mps;
							PCSA = PCSA + ((AxialMuscleMaterial) mpm.getMaterial()).getMaxForce();
						}
					}

				}

			}
			Level.add(String.valueOf(PCSA));
			mGroup.add(Level);
		}
		saveLevelCalculationsInText(mGroupName, mGroup);
	}

	public void saveLevelCalculationsInText(String mgName, ArrayList<ArrayList<String>> mGroup) {

		String fileName = ArtisynthPath.getSrcRelativePath(this, "out/LevelInfo/" + mgName + ".txt");
		File f = new File(fileName);
		try {
			File parent = f.getParentFile();
			parent.mkdirs();
			PrintStream out = new PrintStream(f);

			out.close();
		} catch (FileNotFoundException e) {
			System.err.println("Cannot open file '" + fileName + "' (" + e.getMessage() + ")");
		}
	}

	private class ThetaController extends ControllerBase {

		private double theta;
		private RevoluteJoint rj;
		private RigidTransform3d myXBA;
		private RigidTransform3d rt;

		public ThetaController() {
			myXBA = new RigidTransform3d();
		}

		@Override
		public void apply(double t0, double t1) {

			theta = 0;
			rt = mech.rigidBodies().get("sacrum").getPose();
			myXBA = new RigidTransform3d(rt);
			rt = mech.rigidBodies().get("L5").getPose();
			myXBA.invert();
			myXBA.mul(new RigidTransform3d(rt));
			theta += myXBA.R.m10;

			for (int i = 1; i < 5; i++) {
				rt = mech.rigidBodies().get("L" + Integer.toString(i + 1)).getPose();
				myXBA = new RigidTransform3d(rt);
				rt = mech.rigidBodies().get("L" + Integer.toString(i)).getPose();
				myXBA.invert();
				myXBA.mul(new RigidTransform3d(rt));
				theta += myXBA.R.m10;
			}

			rj = (RevoluteJoint) mech.bodyConnectors().get("Abdjnt");
			rj.setTheta(-40 * theta);

		}

	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		Object source = e.getItemSelectable();

		if (source instanceof JCheckBox) {
			JCheckBox chk = (JCheckBox) source;
			String cmd = chk.getActionCommand();

			for (int i = 0; i < includedMuscleGroupNames.size(); i++) {
				String vcommand = includedMuscleGroupNames.get(i) + "Visible";
				boolean visible = chk.isSelected();

				if (vcommand.equals(cmd)) {
					setMuscleVisible(visible, i);
					rerender();
					return;
				}
			}
		}

	}

	protected void addTrackingPointMonitors(TrackingController t) {
		for (TargetPoint pt : t.getTargetPoints()) {
			TargetPointMonitor mon = new TargetPointMonitor(pt);
			mon.setName(pt.getName() + "_monitor");
			addMonitor(mon);
		}
	}

	protected void addTrackingRotationMonitors(ArrayList<MotionTargetComponent> motionTargets) {
		for (MotionTargetComponent rb : motionTargets) {
			RigidBodyMonitor mon = new RigidBodyMonitor((RigidBody) rb);
			mon.setName(rb.getName() + "_monitor");
			addMonitor(mon);
		}
	}
}

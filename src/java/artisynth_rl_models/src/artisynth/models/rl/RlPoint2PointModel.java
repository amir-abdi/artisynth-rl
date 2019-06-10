package artisynth.models.rl;

import java.awt.Color;
import java.lang.Math;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import artisynth.core.gui.ControlPanel;
import artisynth.core.inverse.ForceTarget;
import artisynth.core.inverse.ForceTargetTerm;
import artisynth.core.inverse.TrackingController;
import artisynth.core.materials.LinearAxialMuscle;
import artisynth.core.mechmodels.AxialSpring;
import artisynth.core.mechmodels.FrameMarker;
import artisynth.core.mechmodels.MechModel;
import artisynth.core.mechmodels.MechSystemSolver.Integrator;
import artisynth.core.mechmodels.MotionTargetComponent;
import artisynth.core.mechmodels.Muscle;
import artisynth.core.mechmodels.MuscleExciter;
import artisynth.core.mechmodels.Particle;
import artisynth.core.mechmodels.Point;
import artisynth.core.mechmodels.RigidBody;
import artisynth.core.modelbase.ComponentList;
import artisynth.core.modelbase.Controller;
import artisynth.core.modelbase.ControllerBase;
import artisynth.core.modelbase.Model;
import artisynth.core.modelbase.StepAdjustment;
import artisynth.core.rl.Log;
import artisynth.core.rl.RlController;
import artisynth.core.rl.RlModelInterface;
import artisynth.core.util.ArtisynthIO;
import artisynth.core.util.ArtisynthPath;
import artisynth.core.workspace.DriverInterface;
import artisynth.core.workspace.PullController;
import artisynth.core.workspace.RootModel;
import maspack.matrix.Point3d;
import maspack.matrix.RigidTransform3d;
import maspack.matrix.Vector3d;
import maspack.render.RenderProps;
import maspack.render.Renderable;
import maspack.render.Renderer;
import maspack.render.Renderer.LineStyle;
import maspack.render.Renderer.Shading;
import maspack.spatialmotion.SpatialInertia;

public class RlPoint2PointModel extends RootModel implements RlModelInterface {

	protected int port = 8080;

	String point_name = "point";
	
	RandomTargetController targetMotionController;
	RlController rlTrack;

	int numMuscles = 12;

	// maximum position in each direction with the current settings for max muscle
	// excitations
	static double POINT_GENERATE_RADIUS = 4.11;
	public static final Vector3d zero = new Vector3d();
	Vector3d disturbance = new Vector3d();
	boolean applyDisturbance = false;
	public static DemoType defaultDemoType = DemoType.Point2d;
	protected DemoType myDemoType = defaultDemoType;
	public ComponentList<MuscleExciter> mex = new ComponentList<MuscleExciter>(MuscleExciter.class, "mex",
			"muscleExcitation");;
	protected MechModel mech;
	protected FrameMarker center;

	public enum DemoType {
		Point1d, Point2d, Point3d, NonSym,
	}

	public boolean useReactionForceTargetP = false;

	double mass = 0.001; // kg
	double len = 10.0;
	double springK = 10.0;
	double springD = 0.1;
	double springRestLen = len * 0.5;

	double muscleF = 1.0;
	double passiveFraction = 0.1;
	double muscleOptLen = len * 0.5;
	double muscleMaxLen = len * 2;
	double muscleD = 0.001;
	double muscleScaleFactor = 1000;
	double pointDamping = 0.0;

	public void build(String[] args) throws IOException {
		parseArgs(args);
		build(myDemoType);
	}

	public void build(DemoType demoType) {
		myDemoType = demoType;

		mech = new MechModel("mech");
		mech.setGravity(0, 0, 0);
		mech.setIntegrator(Integrator.Trapezoidal);
		mech.setMaxStepSize(0.01);
		createModel(myDemoType);
		setupRenderProps();
		addMuscleExciters();
		addRlController();
	}

	private void addMuscleExciters() {
		for (AxialSpring spring : mech.axialSprings()) {
			Muscle muscle = (Muscle)spring;
			MuscleExciter m = new MuscleExciter(muscle.getName() + 'e');
			m.addTarget(muscle, 1.0);
			mex.add(m);
			mech.addMuscleExciter(m);
		}
	}

	public void printType() {
		System.out.println("myType = " + myDemoType.toString());
	}
	
	public String[] generateMuscleLabels() {
		String[] muscleLabels = new String[numMuscles];
		for (int m = 0; m < numMuscles; ++m)
			muscleLabels[m] = "m" + Integer.toString(m);
		return muscleLabels;
	}

	public void createModel(DemoType demoType) {
		String[] muscleLabels = generateMuscleLabels();
		
		switch (demoType) {
		case Point1d: {
			addCenter();
			add1dMuscles(muscleLabels);
			break;
		}
		case Point2d: {
			addCenter();
			add2dLabeledMuscles(muscleLabels);
			break;
		}
		case Point3d: {
			addCenter();
			add3dMuscles();
			break;
		}
		case NonSym: {
			addCenter();
			add2dLabeledMusclesNonSym(muscleLabels);
			break;
		}
		default: {
			System.err.println("PointModel, unknown demo type: " + myDemoType.toString());
		}
		}
		addModel(mech);
	}

	public void setupRenderProps() {
		// set render properties for model

		RenderProps rp = new RenderProps();
		rp.setShading(Shading.SMOOTH);
		rp.setPointStyle(Renderer.PointStyle.SPHERE);
		rp.setPointColor(Color.LIGHT_GRAY);
		rp.setPointRadius(len / 30);
		rp.setLineColor(Color.BLUE.darker());
		rp.setLineStyle(LineStyle.CYLINDER);
		rp.setLineRadius(0.1604);
		mech.setRenderProps(rp);

		RenderProps.setPointColor(center, Color.BLUE);
		RenderProps.setPointRadius(center, len / 25);
	}

	@Override
	public void addController(Controller controller, Model model) {
		super.addController(controller, model);

		if (controller instanceof PullController) {
			PullController pc = ((PullController) controller);
			pc.setStiffness(20);
			RenderProps.setLineColor(pc, Color.RED.darker());
			RenderProps.setPointColor(pc, Color.RED.darker());
			RenderProps.setLineStyle(pc, LineStyle.SOLID_ARROW);
			RenderProps.setLineRadius(pc, 0.25);
		}
		// mech.addForceEffector ((PullController)controller);
	}

	public void addCenter() {
		center = new FrameMarker();
		center.setName(point_name);
		center.setPointDamping(pointDamping);

		RenderProps props = new RenderProps();
		props.setPointColor(Color.BLUE);
		props.setFaceColor(Color.BLUE);
		props.setEdgeColor(Color.BLUE);
		center.setRenderProps(props);	
		
		RigidBody body = new RigidBody(point_name);
		body.setInertia(SpatialInertia.createSphereInertia(mass, len / 25));
		mech.addRigidBody(body);
		RenderProps.setVisible(body, true);

		mech.addFrameMarker(center, body, Point3d.ZERO);		
	}

	public Point3d getRandomTarget(Point3d center, double radius) {
		Random rand = new Random();

		double theta = rand.nextDouble() * 3.1415;
		double phi = rand.nextDouble() * 3.1415;
		double r = rand.nextDouble() * radius;

		double x = r * Math.cos(theta) * Math.sin(phi);
		double y = r * Math.sin(theta) * Math.sin(phi);
		double z = r * Math.cos(phi);

		Vector3d targetVec = new Vector3d(x, y, z);

		if (myDemoType == DemoType.Point2d || myDemoType == DemoType.NonSym)
			targetVec.y = 0;
		if (myDemoType == DemoType.Point1d)
			targetVec.z = 0;

		Point3d targetPnt = new Point3d(targetVec.x, targetVec.y, targetVec.z);
		return targetPnt;
	}

	public StepAdjustment advance(double t0, double t1, int flags) {
		return super.advance(t0, t1, flags);
	}

	public void add2dLabeledMusclesNonSym(String[] labels) {
		addMusclesNonSym(new RigidTransform3d(), labels.length, 0.0);
		int i = 0;
		for (AxialSpring s : mech.axialSprings()) {
			if (s instanceof Muscle) {
				s.setName(labels[i]);
				i += 1;
			}
		}
	}

	public void add2dLabeledMuscles(String[] labels) {
		addMuscles(new RigidTransform3d(), labels.length, 0.0);
		int i = 0;
		for (AxialSpring s : mech.axialSprings()) {
			if (s instanceof Muscle) {
				s.setName(labels[i]);
				i += 1;
			}
		}
	}

	public void add3dMuscles() {
		int[] x = new int[] { -1, 0, 1 };
		int[] y = new int[] { -1, 0, 1 };
		int[] z = new int[] { -1, 0, 1 };

		int muscleCount = 0;
		for (int i = 0; i < x.length; i++) {
			for (int j = 0; j < y.length; j++) {
				for (int k = 0; k < z.length; k++) {
					Point3d pnt = new Point3d(x[i], y[j], z[k]);
					if (pnt.x == 0 || pnt.y == 0 || pnt.z == 0)
						continue;

					pnt.normalize();
					pnt.scale(len);
					Particle endPt = new Particle(mass, pnt);
					endPt.setDynamic(false);
					mech.addParticle(endPt);
					
					Muscle m = addMuscle(endPt);
					m.setName("m" + Integer.toString(muscleCount++));
					RenderProps.setLineColor(m, Color.RED);
				}
			}
		}

	}

	public void add1dMuscles(String[] labels) {
		Log.log("add1dMuscles");
		boolean[] dyn = new boolean[] { false, false };
		int[] x = new int[] { -1, 1 };

		ArrayList<Point> pts = new ArrayList<Point>(x.length);
		for (int i = 0; i < x.length; i++) {
			Point3d pnt = new Point3d(x[i], 0, 0);

			pnt.scale(len);
			Particle pt = new Particle(mass, pnt);
			pt.setPointDamping(pointDamping);
			pt.setDynamic(dyn[i]);
			mech.addParticle(pt);
			pts.add(pt);
			addMuscle(pt);
		}
		int k = 0;
		for (AxialSpring m : mech.axialSprings()) {
			if (m instanceof Muscle) {
				m.setName(labels[k]);
				k += 1;
			}
		}
				
	}

	public void addMuscles() {
		addMuscles(new RigidTransform3d(), 2, 0.0);
	}

	public void addMusclesNonSym(RigidTransform3d X, int num, double offset) {
		double[] disturb = { 1.5, -0.5, 2.5, -0.5, -0.4, -0.5, 0.5, 0.5, 2.5, -0.2, 0.5 };

		for (int i = 0; i < num; i++) {
			double degree = 2 * Math.PI * ((double) i / num) + disturb[i];

			Point3d pnt = new Point3d((len + disturb[i]) * Math.sin(degree), 0.0,
					(len + disturb[i]) * Math.cos(degree));
			pnt.transform(X.R);
			Particle fixed = new Particle(mass, pnt);
			fixed.setDynamic(false);
			mech.addParticle(fixed);
			// System.out.println (pnt);

			addMuscle(fixed);
		}
	}

	public void addMuscles(RigidTransform3d X, int num, double offset) {

		for (int i = 0; i < num; i++) {
			double degree = 2 * Math.PI * ((double) i / num);

			Point3d pnt = new Point3d(len * Math.sin(degree), 0.0, len * Math.cos(degree));
			pnt.transform(X.R);
			Particle fixed = new Particle(mass, pnt);
			fixed.setDynamic(false);
			mech.addParticle(fixed);
			// System.out.println (pnt);

			addMuscle(fixed);
		}
	}

	private Muscle addMuscle(Point endPt) {
		return addMuscle(endPt, center);
	}

	private Muscle addMuscle(Point p0, Point p1) {
		// Muscle m = Muscle.createLinear(muscleF, muscleMaxLen);
		Muscle m = new Muscle();
		// ConstantAxialMuscleMaterial mat = new ConstantAxialMuscleMaterial();
		LinearAxialMuscle mat = new LinearAxialMuscle();
		// PeckAxialMuscleMaterial mat = new PeckAxialMuscleMaterial();
		mat.setMaxForce(muscleF);
		mat.setMaxLength(muscleMaxLen);
		mat.setDamping(muscleD);
		mat.setOptLength(muscleOptLen);
		mat.setPassiveFraction(passiveFraction);
		mat.setForceScaling(muscleScaleFactor);
		m.setMaterial(mat);
		m.setRestLength(len);
		m.setFirstPoint(p0);
		m.setSecondPoint(p1);
		mech.addAxialSpring(m);
		RenderProps.setLineColor(m, Color.RED);

		return m;
	}

	public MechModel getMechModel() {
		return mech;
	}

	@Override
	public void attach(DriverInterface driver) {
		super.attach(driver);

		if (getControlPanels().size() == 0) {
			ControlPanel panel = new ControlPanel("activations", "");
			for (AxialSpring s : mech.axialSprings()) {
				if (s instanceof Muscle) {
					Muscle m = (Muscle) s;
					String name = (m.getName() == null ? "m" + m.getNumber() : m.getName().toUpperCase());
					panel.addWidget(name, m, "excitation", 0.0, 1.0);
				}
			}
			addControlPanel(panel);
		}
	}

	public void addTrackingController() {
		TrackingController myTrackingController = new TrackingController(mech, "tcon");
		for (AxialSpring s : mech.axialSprings()) {
			if (s instanceof Muscle) {
				myTrackingController.addExciter((Muscle) s);
			}
		}

		myTrackingController.addL2RegularizationTerm();
		MotionTargetComponent target = myTrackingController.addMotionTarget(center);
		RenderProps.setPointRadius((Renderable) target, 0.525);

		if (useReactionForceTargetP) {
			ForceTargetTerm forceTerm = new ForceTargetTerm(myTrackingController);
			ForceTarget ft = forceTerm.addForceTarget(mech.bodyConnectors().get("center_constraint"));
			ft.setArrowSize(2);
			RenderProps.setLineStyle(ft, LineStyle.CYLINDER);
			RenderProps.setLineRadius(ft, 0.25);
			forceTerm.setWeight(1d);
			myTrackingController.addForceTargetTerm(forceTerm);
		}

		// myTrackingController.getSolver().setBounds(0.01, 0.99);

		myTrackingController.createProbesAndPanel(this);
		addController(myTrackingController);
	}

	public void loadProbes() {
		String probeFileFullPath = ArtisynthPath.getWorkingDir().getPath() + "/0probes.art";
		System.out.println("Loading Probes from File: " + probeFileFullPath);

		try {
			scanProbes(ArtisynthIO.newReaderTokenizer(probeFileFullPath));
		} catch (Exception e) {
			System.out.println("Error reading probe file");
			e.printStackTrace();
		}
	}

	private void parseArgs(String[] args) {
		for (int i = 0; i < args.length; i += 2) {
			if (args[i].equals("-demoType")) {
				switch (args[i + 1]) {
				case "1d":
					myDemoType = DemoType.Point1d;
					break;
				case "2d":
					myDemoType = DemoType.Point2d;
					break;
				case "3d":
					myDemoType = DemoType.Point3d;
					break;
				case "nonSym":
					myDemoType = DemoType.NonSym;
					break;									
				default:
					myDemoType = DemoType.Point1d;
					break;
				}
				Log.log("Demo type" + myDemoType);
				args[i] = "";
				args[i + 1] = "";
			} else if (args[i].equals("-num")) {
				numMuscles = Integer.parseInt(args[i + 1]);								
				args[i] = "";
				args[i + 1] = "";
			} else if (args[i].equals("-port")) {
				port = Integer.parseInt(args[i + 1]);
			} else if (args[i].equals("-muscleOptLen")) {
				muscleOptLen = Double.parseDouble(args[i + 1]);
			} else if (args[i].equals("-radius")) {
				POINT_GENERATE_RADIUS = Double.parseDouble(args[i + 1]);
			}

		}
	}

	@Override
	public void resetState() {
		targetMotionController.reset();
	}

	@Override
	public void addRlController() {
		rlTrack = new RlController(mech, (RlModelInterface) this, "InvTracker", this.port);

		rlTrack.addMotionTarget(mech.frameMarkers().get(point_name));

		for (MuscleExciter m : mex) {
			Log.log("Add exciter "+ m.getName());
			rlTrack.addExciter(m);
		}

		targetMotionController = new RandomTargetController(rlTrack.getMotionTargets());

		addController(targetMotionController);
		addController(rlTrack);
		addControlPanel(rlTrack.getRlControlPanel());
	}
	
	public class RandomTargetController extends ControllerBase{
		public Boolean reset = false;
		public Boolean trialRun = false;
		Random r = new Random();
		private int time_pos_updated = -1;
		ArrayList<MotionTargetComponent> motionTargetComponents;

		public RandomTargetController(ArrayList<MotionTargetComponent> list) {
			motionTargetComponents = list;
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
		
		private void resetRefPosition() {
			Point point_ref = (Point)motionTargetComponents.get(0);
			Point3d pos = getRandomTarget(new Point3d(0, 0, 0), POINT_GENERATE_RADIUS);
			point_ref.setPosition(pos);
		}
	}

}
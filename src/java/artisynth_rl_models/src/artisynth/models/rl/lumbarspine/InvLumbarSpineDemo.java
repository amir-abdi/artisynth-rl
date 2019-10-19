package artisynth.models.rl.lumbarspine;

import java.awt.Color;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Random;

import javax.swing.JLabel;
import javax.swing.JSeparator;

import maspack.matrix.AxisAngle;
import maspack.matrix.RigidTransform3d;
import maspack.properties.PropertyList;
import maspack.render.RenderProps;
import maspack.render.Renderer.PointStyle;
import artisynth.core.gui.ControlPanel;
import artisynth.core.inverse.MotionTargetTerm;
import artisynth.core.inverse.TrackingController;
import artisynth.core.mechmodels.Frame;
import artisynth.core.mechmodels.MotionTargetComponent;
import artisynth.core.mechmodels.MuscleExciter;
import artisynth.core.mechmodels.RigidBody;
import artisynth.core.modelbase.ControllerBase;
import artisynth.core.rl.Log;
import artisynth.core.rl.RlTargetControllerInterface;
import artisynth.core.workspace.DriverInterface;

public class InvLumbarSpineDemo extends LumbarSpineBaseDemo {

	protected double flexAll;
	protected double flexThorax;
	protected double flexL1;
	protected double flexL2;
	protected double flexL3;
	protected double flexL4;
	protected double flexL5;

	protected AxisAngle thoraxOrientation;
	protected AxisAngle thoraxOrientation_ref;
	protected AxisAngle L1Orientation;
	protected AxisAngle L1Orientation_ref;
	protected AxisAngle L2Orientation;
	protected AxisAngle L2Orientation_ref;
	protected AxisAngle L3Orientation;
	protected AxisAngle L3Orientation_ref;
	protected AxisAngle L4Orientation;
	protected AxisAngle L4Orientation_ref;
	protected AxisAngle L5Orientation;
	protected AxisAngle L5Orientation_ref;
	protected ArrayList<AxisAngle> sourceOrientations;
	protected ArrayList<AxisAngle> targetOrientations;

	public ControlPanel myTargetPositionerPanel;
	protected ArrayList<MotionTargetComponent> motionTargetComponents;
	protected ArrayList<Frame> motionRealComponents;

	protected String[] sourceRigidBodies = { "thorax", "L1", "L2", "L3", "L4", "L5" };	

	public static PropertyList myProps = new PropertyList(InvLumbarSpineDemo.class, LumbarSpineBaseDemo.class);

	public PropertyList getAllPropertyInfo() {
		return myProps;
	}

	static {

		myProps.add("flexAll * *", "flexes all targets evenly", 0.0, "[-60.0,60.0] NW");
		myProps.add("flexThorax * *", "flexes thorax target", 0.0, "[-20.0,20.0] NW");
		myProps.add("flexL1 * *", "flexs L1 target", 0.0, "[-20.0,20.0] NW");
		myProps.add("flexL2 * *", "flexs L2 target", 0.0, "[-20.0,20.0] NW");
		myProps.add("flexL3 * *", "flexs L3 target", 0.0, "[-20.0,20.0] NW");
		myProps.add("flexL4 * *", "flexs L4 target", 0.0, "[-20.0,20.0] NW");
		myProps.add("flexL5 * *", "flexs L5 target", 0.0, "[-20.0,20.0] NW");

		myProps.addReadOnly("thoraxOrientation *", "current orientation of the thorax in the model");
		myProps.addReadOnly("thoraxOrientation_ref *", "reference (prescribed) orientation of the thorax in the model");
		myProps.addReadOnly("L1Orientation *", "current orientation of L1 in the model");
		myProps.addReadOnly("L1Orientation_ref *", "reference (prescribed) orientation of L1 in the model");
		myProps.addReadOnly("L2Orientation *", "current orientation of L2 in the model");
		myProps.addReadOnly("L2Orientation_ref *", "reference (prescribed) orientation of L2 in the model");
		myProps.addReadOnly("L3Orientation *", "current orientation of L3 in the model");
		myProps.addReadOnly("L3Orientation_ref *", "reference (prescribed) orientation of L3 in the model");
		myProps.addReadOnly("L4Orientation *", "current orientation of L4 in the model");
		myProps.addReadOnly("L4Orientation_ref *", "reference (prescribed) orientation of L4 in the model");
		myProps.addReadOnly("L5Orientation *", "current orientation of L5 in the model");
		myProps.addReadOnly("L5Orientation_ref *", "reference (prescribed) orientation of L5 in the model");
	}

	public InvLumbarSpineDemo(String name) {
		this(name, true);
	}

	public void addRealComponents() {
		motionRealComponents = new ArrayList<Frame>();
		for (String src_rb_str : sourceRigidBodies) {
			RigidBody rb = mech.rigidBodies().get(src_rb_str);
			motionRealComponents.add(rb);
		}

		Log.debug("orientations added to lists");
		sourceOrientations = new ArrayList<AxisAngle>();
		sourceOrientations.add(thoraxOrientation);
		sourceOrientations.add(L1Orientation);
		sourceOrientations.add(L2Orientation);
		sourceOrientations.add(L3Orientation);
		sourceOrientations.add(L4Orientation);
		sourceOrientations.add(L5Orientation);

		targetOrientations = new ArrayList<AxisAngle>();
		targetOrientations.add(thoraxOrientation_ref);
		targetOrientations.add(L1Orientation_ref);
		targetOrientations.add(L2Orientation_ref);
		targetOrientations.add(L3Orientation_ref);
		targetOrientations.add(L4Orientation_ref);
		targetOrientations.add(L5Orientation_ref);

//		RigidBody rb = mech.rigidBodies().get("thorax");
//		motionRealComponents.add(rb);
//
//		rb = mech.rigidBodies().get("L1");
//		motionRealComponents.add(rb);
//
//		rb = mech.rigidBodies().get("L2");
//		motionRealComponents.add(rb);
//
//		rb = mech.rigidBodies().get("L3");
//		motionRealComponents.add(rb);
//
//		rb = mech.rigidBodies().get("L4");
//		motionRealComponents.add(rb);
//
//		rb = mech.rigidBodies().get("L5");
//		motionRealComponents.add(rb);
	}

	public InvLumbarSpineDemo(String name, Boolean inverseSolver) {
		super(name);
		addMonitors();
		setPositions();
		addRealComponents();
		if (inverseSolver)
			addInverseSolver();
	}

	// ********************************************************
	// ******************* Adding ATTACH DRIVER****************
	// ********************************************************
	public void attach(DriverInterface driver) {
		super.attach(driver);

		addTargetPositionerControlPanel();
		// adjustControlPanelLocations ();
	}

	// ********************************************************
	// ******************* Adding Control Panels **************
	// ********************************************************
	public void addTargetPositionerControlPanel() {

		myTargetPositionerPanel = new ControlPanel("TargetPositioner", "LiveUpdate");
		myTargetPositionerPanel.addWidget(new JLabel("Adjusting Target Position:"));
		myTargetPositionerPanel.addWidget("    Flex All", this, "flexAll");
		myTargetPositionerPanel.addWidget("    Flex L1", this, "flexL1");
		myTargetPositionerPanel.addWidget("    Flex L2", this, "flexL2");
		myTargetPositionerPanel.addWidget("    Flex L3", this, "flexL3");
		myTargetPositionerPanel.addWidget("    Flex L4", this, "flexL4");
		myTargetPositionerPanel.addWidget("    Flex L5", this, "flexL5");

		myTargetPositionerPanel.addWidget(new JSeparator());
		myTargetPositionerPanel.addWidget(new JLabel("Reference (Target) Orientation (i.e. prescribed to the model):"));
		myTargetPositionerPanel.addWidget("    Thorax Orientation", this, "thoraxOrientation_ref");
		myTargetPositionerPanel.addWidget("    L1 Orientation", this, "L1Orientation_ref");
		myTargetPositionerPanel.addWidget("    L2 Orientation", this, "L2Orientation_ref");
		myTargetPositionerPanel.addWidget("    L3 Orientation", this, "L3Orientation_ref");
		myTargetPositionerPanel.addWidget("    L4 Orientation", this, "L4Orientation_ref");
		myTargetPositionerPanel.addWidget("    L5 Orientation", this, "L5Orientation_ref");

		myTargetPositionerPanel.addWidget(new JSeparator());
		myTargetPositionerPanel.addWidget(new JLabel("Real (Source) Orientation (i.e. produced by the model):"));
		myTargetPositionerPanel.addWidget("    Thorax Orientation", this, "thoraxOrientation");
		myTargetPositionerPanel.addWidget("    L1 Orientation", this, "L1Orientation");
		myTargetPositionerPanel.addWidget("    L2 Orientation", this, "L2Orientation");
		myTargetPositionerPanel.addWidget("    L3 Orientation", this, "L3Orientation");
		myTargetPositionerPanel.addWidget("    L4 Orientation", this, "L4Orientation");
		myTargetPositionerPanel.addWidget("    L5 Orientation", this, "L5Orientation");

		myTargetPositionerPanel.addWidget(new JSeparator());

		myTargetPositionerPanel.setScrollable(false);

		addControlPanel(myTargetPositionerPanel);
	}

	public void adjustControlPanelLocations() {
		ControlPanel cp;
		int visibilityHeight;
		cp = getControlPanels().get("visibility");
		Dimension visibilityD = cp.getSize();
		java.awt.Point loc = getMainFrame().getLocation();

		cp = getControlPanels().get("TargetPositioner");
		cp.setLocation(loc.x + visibilityD.width, loc.y);
		visibilityHeight = visibilityD.height;

		cp = getControlPanels().get("FrameSpringsForces");
		cp.setLocation(loc.x + visibilityD.width, loc.y + visibilityHeight);
	}

	public void setPositions() {
		setFlexAll(0);
		setFlexThorax(0);
		setFlexL1(0);
		setFlexL2(0);
		setFlexL3(0);
		setFlexL4(0);
		setFlexL5(0);

		Log.debug("Positions set");
		setThoraxOrientation(new AxisAngle(0, 0, 1, 0));
		setL1Orientation(new AxisAngle(0, 0, 1, 0));
		setL2Orientation(new AxisAngle(0, 0, 1, 0));
		setL3Orientation(new AxisAngle(0, 0, 1, 0));
		setL4Orientation(new AxisAngle(0, 0, 1, 0));
		setL5Orientation(new AxisAngle(0, 0, 1, 0));

		setThoraxOrientation_ref(new AxisAngle(0, 0, 1, 0));
		setL1Orientation_ref(new AxisAngle(0, 0, 1, 0));
		setL2Orientation_ref(new AxisAngle(0, 0, 1, 0));
		setL3Orientation_ref(new AxisAngle(0, 0, 1, 0));
		setL4Orientation_ref(new AxisAngle(0, 0, 1, 0));
		setL5Orientation_ref(new AxisAngle(0, 0, 1, 0));
	}

	public void setOrientationIndex(int i, AxisAngle axisAngle) {
		sourceOrientations.set(i, axisAngle);
	}

	public void setOrientationRefIndex(int i, AxisAngle axisAngle) {
		targetOrientations.set(i, axisAngle);
	}

	public double getFlexAll() {
		return flexAll;
	}

	public void setFlexAll(double flexAll) {
		this.flexAll = flexAll;
	}

	public double getFlexThorax() {
		return flexThorax;
	}

	public void setFlexThorax(double flexThorax) {
		this.flexThorax = flexThorax;
	}

	public double getFlexL1() {
		return flexL1;
	}

	public void setFlexL1(double flexL1) {
		this.flexL1 = flexL1;
	}

	public double getFlexL2() {
		return flexL2;
	}

	public void setFlexL2(double flexL2) {
		this.flexL2 = flexL2;
	}

	public double getFlexL3() {
		return flexL3;
	}

	public void setFlexL3(double flexL3) {
		this.flexL3 = flexL3;
	}

	public double getFlexL4() {
		return flexL4;
	}

	public void setFlexL4(double flexL4) {
		this.flexL4 = flexL4;
	}

	public double getFlexL5() {
		return flexL5;
	}

	public void setFlexL5(double flexL5) {
		this.flexL5 = flexL5;
	}

	public AxisAngle getThoraxOrientation() {
		return thoraxOrientation;
	}

	public void setThoraxOrientation(AxisAngle thoraxOrientation) {
		this.thoraxOrientation = thoraxOrientation;
	}

	public AxisAngle getThoraxOrientation_ref() {
		return thoraxOrientation_ref;
	}

	public void setThoraxOrientation_ref(AxisAngle thoraxOrientation_ref) {
		this.thoraxOrientation_ref = thoraxOrientation_ref;
	}

	public AxisAngle getL1Orientation() {
		return L1Orientation;
	}

	public void setL1Orientation(AxisAngle l1Orientation) {
		L1Orientation = l1Orientation;
	}

	public AxisAngle getL1Orientation_ref() {
		return L1Orientation_ref;
	}

	public void setL1Orientation_ref(AxisAngle l1Orientation_ref) {
		L1Orientation_ref = l1Orientation_ref;
	}

	public AxisAngle getL2Orientation() {
		return L2Orientation;
	}

	public void setL2Orientation(AxisAngle l2Orientation) {
		L2Orientation = l2Orientation;
	}

	public AxisAngle getL2Orientation_ref() {
		return L2Orientation_ref;
	}

	public void setL2Orientation_ref(AxisAngle l2Orientation_ref) {
		L2Orientation_ref = l2Orientation_ref;
	}

	public AxisAngle getL3Orientation() {
		return L3Orientation;
	}

	public void setL3Orientation(AxisAngle l3Orientation) {
		L3Orientation = l3Orientation;
	}

	public AxisAngle getL3Orientation_ref() {
		return L3Orientation_ref;
	}

	public void setL3Orientation_ref(AxisAngle l3Orientation_ref) {
		L3Orientation_ref = l3Orientation_ref;
	}

	public AxisAngle getL4Orientation() {
		return L4Orientation;
	}

	public void setL4Orientation(AxisAngle l4Orientation) {
		L4Orientation = l4Orientation;
	}

	public AxisAngle getL4Orientation_ref() {
		return L4Orientation_ref;
	}

	public void setL4Orientation_ref(AxisAngle l4Orientation_ref) {
		L4Orientation_ref = l4Orientation_ref;
	}

	public AxisAngle getL5Orientation() {
		return L5Orientation;
	}

	public void setL5Orientation(AxisAngle l5Orientation) {
		L5Orientation = l5Orientation;
	}

	public AxisAngle getL5Orientation_ref() {
		return L5Orientation_ref;
	}

	public void setL5Orientation_ref(AxisAngle l5Orientation_ref) {
		L5Orientation_ref = l5Orientation_ref;
	}

	// ********************************************************
	// ******************* Adding Controllers ****************
	// ********************************************************

	public void addInverseSolver() {

		double w_u = 1.0, w_r = 0.01, w_d = 0.0001;

		double point_radius = 0.01;

		TrackingController invTrack = new TrackingController(mech, "InvTracker");

		motionRealComponents = new ArrayList<Frame>();

		RigidBody rb = mech.rigidBodies().get("thorax");
		invTrack.addMotionTarget(rb);
		motionRealComponents.add(rb);

		rb = mech.rigidBodies().get("L1");
		invTrack.addMotionTarget(rb);
		motionRealComponents.add(rb);

		rb = mech.rigidBodies().get("L2");
		invTrack.addMotionTarget(rb);
		motionRealComponents.add(rb);

		rb = mech.rigidBodies().get("L3");
		invTrack.addMotionTarget(rb);
		motionRealComponents.add(rb);

		rb = mech.rigidBodies().get("L4");
		invTrack.addMotionTarget(rb);
		motionRealComponents.add(rb);

		rb = mech.rigidBodies().get("L5");
		invTrack.addMotionTarget(rb);
		motionRealComponents.add(rb);

		invTrack.setUseTrapezoidalSolver(-1); // automatic
		invTrack.setKeepVelocityJacobianConstant(false);
		MotionTargetTerm mt = invTrack.getMotionTargetTerm();
		mt.setWeight(w_u);
		mt.setKd(0.25); // Peter's edit

		for (MuscleExciter m : mex) {
			invTrack.addExciter(m);
		}

		motionTargetComponents = invTrack.getMotionTargets();

		RenderProps rp1 = new RenderProps();
		rp1.setPointStyle(PointStyle.SPHERE);
		rp1.setPointRadius(point_radius);
		rp1.setPointColor(Color.CYAN);

		RenderProps rp2 = new RenderProps(rp1);
		rp2.setPointColor(Color.RED);
		rp2.setPointRadius(point_radius);
		invTrack.setMotionRenderProps(rp1, rp2);

		SimpleTargetController targetMotionController = new SimpleTargetController(invTrack.getMotionTargets());

		invTrack.addL2RegularizationTerm(w_r);
		invTrack.addDampingTerm(w_d);

		addController(targetMotionController);
		addController(invTrack);
		invTrack.createPanel(this);

		addTrackingRotationMonitors(invTrack.getMotionTargets());
	}

	public class SimpleTargetController extends ControllerBase  {

		ArrayList<MotionTargetComponent> motionTargetComponents;
		RigidTransform3d ipos;
		public Frame frm;

		public SimpleTargetController() {

		}

		public SimpleTargetController(ArrayList<MotionTargetComponent> list) {
			motionTargetComponents = list;
		}

		protected void applyFlexAll(double rotation) {
			// correction factor so flexion values become positive
			double CF = -1;

			double num = motionTargetComponents.size();
			for (int i = 0; i < num; ++i) {
				RigidBody source_rb = mech.rigidBodies().get(sourceRigidBodies[i]);
				setOrientationIndex(i, source_rb.getOrientation());

				ipos = new RigidTransform3d();
				ipos.mulRotZ(Math.toRadians(CF * (flexL1 + (num - i) / num * flexAll)));
				ipos.p.add(source_rb.getPosition());
				((Frame) motionTargetComponents.get(i)).setPose(ipos);
				setOrientationRefIndex(i, ((Frame) motionTargetComponents.get(i)).getOrientation());
			}

		}

		public void apply(double t0, double t1) {

			if (t0 > -1) {
				applyFlexAll(flexAll);
			}
		}
	}

	public class RandomTargetController extends SimpleTargetController implements RlTargetControllerInterface {
		public Boolean reset = false;
		public Boolean trialRun = false;
		private double MAX_FLEX_EXTEND = 30;
		// TODO: use the rlController random instead of this one
		Random r = new Random();
		private int time_pos_updated = -1;

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
						flexAll = (r.nextDouble() - 0.5) * MAX_FLEX_EXTEND * 2;
						time_pos_updated = (int) t0;
					}
				} else if (reset) {
					reset = false;
					flexAll = (r.nextDouble() - 0.5) * MAX_FLEX_EXTEND * 2;
				}
				
				applyFlexAll(flexAll);

			}

		}
	}

}

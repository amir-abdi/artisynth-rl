/**
 * Copyright (c) 2019, by the Authors: Amir Abdi (UBC)
 *
 * This software is freely available under a 2-clause BSD license. Please see
 * the LICENSE file in the ArtiSynth distribution directory for details.
 */
package artisynth.core.rl;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import artisynth.core.gui.ControlPanel;
import artisynth.core.inverse.TargetFrame;
import artisynth.core.inverse.TargetPoint;
import artisynth.core.mechmodels.ExcitationComponent;
import artisynth.core.mechmodels.Frame;
import artisynth.core.mechmodels.MechSystemBase;
import artisynth.core.mechmodels.MotionTargetComponent;
import artisynth.core.mechmodels.MultiPointMuscle;
import artisynth.core.mechmodels.Muscle;
import artisynth.core.mechmodels.Point;
import artisynth.core.mechmodels.PointList;
import artisynth.core.mechmodels.RigidBody;
import artisynth.core.mechmodels.MotionTarget.TargetActivity;
import artisynth.core.modelbase.ComponentChangeEvent;
import artisynth.core.modelbase.ComponentList;
import artisynth.core.modelbase.ComponentListImpl;
import artisynth.core.modelbase.ComponentUtils;
import artisynth.core.modelbase.CompositeComponent;
import artisynth.core.modelbase.ControllerBase;
import artisynth.core.modelbase.ModelComponent;
import artisynth.core.modelbase.ReferenceList;
import artisynth.core.modelbase.RenderableComponent;
import artisynth.core.modelbase.RenderableComponentList;

import maspack.geometry.PolygonalMesh;
import maspack.properties.PropertyList;
import maspack.render.RenderList;
import maspack.render.RenderProps;
import maspack.render.Renderable;
import maspack.render.Renderer.FaceStyle;
import maspack.render.Renderer.PointStyle;
import maspack.util.ReaderTokenizer;

public class RlController extends ControllerBase
		implements CompositeComponent, RenderableComponent, RlControllerInterface {

	RlModelInterface myInverseModel;
	protected ComponentListImpl<ModelComponent> myComponents;

	// list of target points that store the location of motion targets
	protected PointList<TargetPoint> targetPoints;

	// list of target frames that store the location and rotation of targets
	protected RenderableComponentList<TargetFrame> targetFrames;

	// reference lists to original points and frames
	protected ReferenceList sourcePoints;
	protected ReferenceList sourceFrames;

	protected ArrayList<MotionTargetComponent> mySources;
	protected ArrayList<MotionTargetComponent> myTargets;

	// list of all muscle exciters
	protected ComponentList<ExcitationComponent> exciters;

	RlRestApi networkHandler;
	protected MechSystemBase myMech;
	private String name;

	protected RenderProps targetRenderProps;
	protected RenderProps sourceRenderProps;

	protected boolean targetsVisible = true;
	protected boolean sourcesVisible = true;
	protected boolean enabled = true;
	protected boolean debug = true;
	protected double targetsPointRadius = DEFAULT_TARGET_RADIUS;
	protected int targetsLineWidth = DEFAULT_TARGET_LINE_WIDTH;
	public static final boolean DEFAULT_DEBUG = true;
	private static final double DEFAULT_TARGET_RADIUS = 0.4d;
	private static final int DEFAULT_TARGET_LINE_WIDTH = 2;

	/**
	 * Set the Rl model which implements the RlModelInterface interface.
	 */
	public void setInverseModel(RlModelInterface model) {
		myInverseModel = model;
	}

	/**
	 * Creates and names a tracking controller for a given mech system
	 * 
	 * @param m    mech system, typically your "MechModel"
	 * @param name name to give the controller
	 */
	public RlController(MechSystemBase m, RlModelInterface model, String name, int port) {
		super();
		setMech(m);
		setName(name);
		setInverseModel(model);

		networkHandler = new RlRestApi(this, port);

		myComponents = new ComponentListImpl<ModelComponent>(ModelComponent.class, this);
		mySources = new ArrayList<MotionTargetComponent>();
		myTargets = new ArrayList<MotionTargetComponent>();

		// setup points
		targetPoints = new PointList<TargetPoint>(TargetPoint.class, "targetPoints");
		// always show this component, even if it's empty
		targetPoints.setNavpanelVisibility(NavpanelVisibility.ALWAYS);
		add(targetPoints);
		sourcePoints = new ReferenceList("sourcePoints");
		add(sourcePoints);

		// setup frames
		targetFrames = new RenderableComponentList<TargetFrame>(TargetFrame.class, "targetFrames");
		// always show this component, even if it's empty:
		targetFrames.setNavpanelVisibility(NavpanelVisibility.ALWAYS);
		add(targetFrames);
		sourceFrames = new ReferenceList("sourceFrames");
		add(sourceFrames);

		exciters = new ComponentList<ExcitationComponent>(ExcitationComponent.class, "excitationSources");
		// always show this component, even if it's empty:
		exciters.setNavpanelVisibility(NavpanelVisibility.ALWAYS);
		add(exciters);

		initTargetRenderProps();
		initSourceRenderProps();
	}

	@Override
	public void prerender(RenderList list) {
		super.prerender(list);
		recursivelyPrerender(this, list);
	}

	protected void recursivelyPrerender(CompositeComponent comp, RenderList list) {

		for (int i = 0; i < comp.numComponents(); i++) {
			ModelComponent c = comp.get(i);
			if (c instanceof Renderable) {
				list.addIfVisible((Renderable) c);
			} else if (c instanceof CompositeComponent) {
				recursivelyPrerender((CompositeComponent) c, list);
			}
		}
	}

	public void setMech(MechSystemBase m) {
		setModel(m);
		myMech = m;
	}

	protected void add(ModelComponent comp) {
		myComponents.add(comp);
	}

	public MotionTargetComponent addMotionTarget(MotionTargetComponent source) {
		mySources.add(source);
		MotionTargetComponent target = null;
		if (source instanceof Point) {
			target = addTargetPoint((Point) source);
		} else if (source instanceof Frame) {
			target = addTargetFrame((RigidBody) source);
		} else
			throw new NotImplementedException();
		return target;
	}

	private TargetPoint addTargetPoint(Point source) {
		TargetPoint tpnt = new TargetPoint();
		tpnt.setName((source.getName() != null ? source.getName() : String.format("p%d", source.getNumber())) + "_ref");
		tpnt.setState(source);
		tpnt.setTargetActivity(TargetActivity.PositionVelocity);
		myTargets.add(tpnt);

		targetPoints.add(tpnt);

		return tpnt;
	}

	private TargetFrame addTargetFrame(RigidBody source) {
		TargetFrame tframe = new TargetFrame();
		tframe.setPose(source.getPose());
		tframe.setName(
				(source.getName() != null ? source.getName() : String.format("rb%d", source.getNumber())) + "_ref");

		tframe.setState(source);
		tframe.setTargetActivity(TargetActivity.PositionVelocity);

		myTargets.add(tframe);

		// add mesh to TargetFrame
		PolygonalMesh mesh = null;
		if ((mesh = source.getSurfaceMesh()) != null) {
			tframe.setSurfaceMesh(mesh.clone(), source.getSurfaceMeshComp().getFileName());
			tframe.setRenderProps(source.getRenderProps());
			RenderProps.setDrawEdges(tframe, true);
			RenderProps.setFaceStyle(tframe, FaceStyle.NONE);
		}

		targetFrames.add(tframe);
		return tframe;
	}

	/**
	 * Adds an exciter to be used as a free variable in the inverse routine
	 * 
	 * @param weight regularization weight to be applied to the exciter
	 * @param ex     exciter to add
	 * @param gain   the gain applied to the exciter
	 */
	public void addExciter(ExcitationComponent ex) {
		exciters.add(ex);

		if (ex instanceof MultiPointMuscle) {
			MultiPointMuscle m = (MultiPointMuscle) ex;
			if (m.getExcitationColor() == null) {
				RenderProps.setLineColor(m, Color.WHITE);
				m.setExcitationColor(Color.RED);
			}
		} else if (ex instanceof Muscle) {
			Muscle m = (Muscle) ex;
			if (m.getExcitationColor() == null) {
				RenderProps.setLineColor(m, Color.WHITE);
				m.setExcitationColor(Color.RED);
			}
		}
	}

	/**
	 * Returns the set of targets
	 */
	public ArrayList<MotionTargetComponent> getMotionTargets() {
		return myTargets;
	}

	public void setTargetRenderProps(RenderProps rend) {
		targetRenderProps.set(rend);

		targetPoints.setRenderProps(targetRenderProps);
		targetFrames.setRenderProps(targetRenderProps);
	}

	public void setSourceRenderProps(RenderProps rend) {
		sourceRenderProps.set(rend);

		for (MotionTargetComponent p : mySources) {
			if (p instanceof Point) {
				((Point) p).setRenderProps(sourceRenderProps);
			} else if (p instanceof Frame) {
				((Frame) p).setRenderProps(sourceRenderProps);
			}
		}
	}

	public void initTargetRenderProps() {
		targetRenderProps = new RenderProps();
		targetRenderProps.setDrawEdges(true);
		targetRenderProps.setFaceStyle(FaceStyle.NONE);
		targetRenderProps.setLineColor(Color.CYAN);
		targetRenderProps.setLineWidth(2);
		targetRenderProps.setPointColor(Color.CYAN);
		targetRenderProps.setPointStyle(PointStyle.SPHERE);
		// set target point radius explicitly
		targetRenderProps.setPointRadius(0.3);
		targetRenderProps.setVisible(true);
		targetRenderProps.setPointRadius(targetsPointRadius);

		targetPoints.setRenderProps(targetRenderProps);
		targetFrames.setRenderProps(targetRenderProps);
	}

	public void initSourceRenderProps() {
		sourceRenderProps = new RenderProps();
		sourceRenderProps.setDrawEdges(true);
		sourceRenderProps.setFaceStyle(FaceStyle.NONE);
		sourceRenderProps.setLineColor(Color.CYAN);
		sourceRenderProps.setLineWidth(2);
		sourceRenderProps.setPointColor(Color.CYAN);
		sourceRenderProps.setPointStyle(PointStyle.SPHERE);
		sourceRenderProps.setVisible(true);
		;
		// modRenderProps.setAlpha(0.5);

		setSourceRenderProps(sourceRenderProps);
	}

	public ArrayList<MotionTargetComponent> getTargets() {
		return myTargets;
	}

	public ArrayList<MotionTargetComponent> getSources() {
		return mySources;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) throws IllegalArgumentException {
		this.name = name;
	}

	@Override
	public boolean hasState() {
		return false;
	}

	@Override
	public void scan(ReaderTokenizer rtok, Object ref) throws IOException {
		super.scan(rtok, ref);
	}

	@Override
	public ModelComponent get(String nameOrNumber) {
		return myComponents.get(nameOrNumber);
	}

	@Override
	public ModelComponent get(int idx) {
		return myComponents.get(idx);
	}

	@Override
	public ModelComponent getByNumber(int num) {
		return myComponents.getByNumber(num);
	}

	@Override
	public int numComponents() {
		return myComponents.size();
	}

	@Override
	public int indexOf(ModelComponent comp) {
		return myComponents.indexOf(comp);
	}

	@Override
	public ModelComponent findComponent(String path) {
		return ComponentUtils.findComponent(this, path);
	}

	@Override
	public int getNumberLimit() {
		return myComponents.getNumberLimit();
	}

	@Override
	public NavpanelDisplay getNavpanelDisplay() {
		return NavpanelDisplay.NORMAL;
	}

	@Override
	public void componentChanged(ComponentChangeEvent e) {
		myComponents.componentChanged(e);
		notifyParentOfChange(e);
	}

	@Override
	public void updateNameMap(String newName, String oldName, ModelComponent comp) {
		myComponents.updateNameMap(newName, oldName, comp);
	}

	@Override
	public boolean hierarchyContainsReferences() {
		return false;
	}

	@Override
	public PropertyList getAllPropertyInfo() {
		return myProps;
	}

	public static PropertyList myProps = new PropertyList(RlController.class, ControllerBase.class);
	static {
		myProps.add("renderProps * *", "render properties", null);
		myProps.add("enabled isEnabled *", "enable/disable controller", true);
		myProps.add("targetsVisible * *", "allow showing or hiding of targets markers", true);
		myProps.add("sourcesVisible * *", "allow showing or hiding of source markers", true);
		myProps.add("targetsPointRadius * *", "set size of target markers", DEFAULT_TARGET_RADIUS);
		myProps.add("targetsLineWidth * *", "set width of target lines", DEFAULT_TARGET_LINE_WIDTH);
		myProps.add("debug isDebug", "enables output of debug info to the console", DEFAULT_DEBUG);
	}

	public ControlPanel getRlControlPanel() {
		ControlPanel cp = new ControlPanel("RlControlPanel");

		cp.addWidget("Enabled", this, "enabled");

		cp.addWidget(new JSeparator());
		cp.addWidget(new JLabel("Render"));
		cp.addWidget("Sources", this, "sourcesVisible");

		cp.addWidget("Target", this, "targetsVisible");
		cp.addWidget("Targets Point Radius", this, "targetsPointRadius");
		cp.addWidget("Targets Line Width", this, "targetsLineWidth");

		cp.addWidget(new JSeparator());
		cp.addWidget("Debug", this, "debug");

		return cp;
	}

	/**
	 * Show or hide the sources
	 */
	public void setSourcesVisible(boolean show) {
		ArrayList<MotionTargetComponent> moTargetParticles = mySources;
		for (MotionTargetComponent p : moTargetParticles) {
			if (p instanceof RenderableComponent) {
				RenderProps.setVisible((RenderableComponent) p, show);
			}
		}
		sourcesVisible = show;
	}

	public void setTargetsPointRadius(double radius) {
		targetsPointRadius = radius;
		RenderProps.setPointRadius(targetPoints, radius);
	}

	public double getTargetsPointRadius() {
		return targetsPointRadius;
	}

	public void setTargetsLineWidth(int width) {
		targetsLineWidth = width;
		RenderProps.setLineWidth(targetFrames, width);
	}

	public int getTargetsLineWidth() {
		return targetsLineWidth;
	}

	public boolean getSourcesVisible() {
		return sourcesVisible;
	}

	public boolean getTargetsVisible() {
		return targetsVisible;
	}

	public void setTargetsVisible(boolean visible) {
		ArrayList<MotionTargetComponent> moTargetParticles = getTargets();
		for (MotionTargetComponent p : moTargetParticles) {
			if (p instanceof RenderableComponent) {
				RenderProps.setVisible((RenderableComponent) p, visible);
			}
		}
		targetsVisible = visible;
	}

	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
		Log.logging = this.debug;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public void apply(double t0, double t1) {
		// A controller should implement the apply method, but you can leave this empty
		// as the control is done
		// via the RlRestApi
	}

	// --------------- Implement RlControllerInterface ---------
	public int getStateSize() {
		return getState().size();
	}

	public int getActionSize() {
		return getExcitations().size();
	}

	public RlState getState() {
		Log.log("Get State");

		// real current position
		ArrayList<MotionTargetComponent> sources = getSources();

		// destination, ref, target
		ArrayList<MotionTargetComponent> targets = getTargets();

		// results
		RlState rlState = new RlState();
		rlState.addAll(getRlComponents(sources));
		rlState.addAll(getRlComponents(targets));

		Log.log("state.size = " + rlState.numComponents());
		return rlState;
	}

	private ArrayList<RlComponent> getRlComponents(ArrayList<MotionTargetComponent> comps) {
		ArrayList<RlComponent> state = new ArrayList<RlComponent>(comps.size());
		for (MotionTargetComponent component : comps) {
			if (component instanceof Point) {
				RlComponent rlComponent = new RlComponent();
				double[] values = new double[3];

				rlComponent.setName(component.getName());

				((Point) component).getVelocity().get(values);
				rlComponent.setVelocity(values);

				((Point) component).getPosition().get(values);
				rlComponent.setPosition(values);

				state.add(rlComponent);

			} else if (component instanceof Frame) {
				RlComponent rlComponent = new RlComponent();
				double[] values = new double[3];

				rlComponent.setName(component.getName());

				((Frame) component).getPosition().get(values);
				rlComponent.setPosition(values);

				values = new double[4];
				// Rotation is sent as a quaternion
				((Frame) component).getRotation().get(values);
				rlComponent.setOrientation(values);

				values = new double[6];
				((Frame) component).getVelocity().get(values);
				rlComponent.setTwist(values);

				state.add(rlComponent);
			} else
				throw new NotImplementedException();
		}
		return state;
	}

	@Override
	public void setExcitations(ArrayList<Double> excitations) {
		for (int i = 0; i < excitations.size(); i++) {
			exciters.get(i).setExcitation(excitations.get(i));
		}
	}

	@Override
	public ArrayList<Double> getExcitations() {
		ArrayList<Double> exs = new ArrayList<Double>(exciters.size());

		for (ExcitationComponent m : this.exciters) {
			exs.add(m.getExcitation());
		}
		return exs;
	}

	@Override
	public String resetState() {
		myInverseModel.resetState();
		return "Reset Done";
	}

	/**
	 * Clears all terms and disposes storage
	 */
	public void dispose() {
		// System.out.println("tracking controller dispose()");
		targetPoints.clear();
		remove(targetPoints);
		targetFrames.clear();
		remove(targetFrames);
		sourcePoints.clear();
		remove(sourcePoints);
		sourceFrames.clear();
		remove(sourceFrames);

		for (@SuppressWarnings("unused")
		ExcitationComponent excCom : exciters) {

		}
		exciters.clear();
		remove(exciters);
	}

	protected boolean remove(ModelComponent comp) {
		return myComponents.remove(comp);
	}

	ControlPanel controlPanel;

	public void createPanel() {
//	      Main.getMain ().getInverseManager ().showInversePanel (root, this);		

	}
}

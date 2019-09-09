package artisynth.models.rl.jaw;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;

import artisynth.core.femmodels.FemElement3d;
import artisynth.core.femmodels.FemFactory;
import artisynth.core.femmodels.FemModel3d;
import artisynth.core.femmodels.FemNode;
import artisynth.core.femmodels.FemNode3d;
import artisynth.core.femmodels.PointFem3dAttachment;
import artisynth.core.materials.AxialMaterial;
import artisynth.core.materials.AxialMuscleMaterial;
import artisynth.core.materials.IncompressibleMaterial;
import artisynth.core.materials.LigamentAxialMaterial;
import artisynth.core.materials.LinearMaterial;
import artisynth.core.materials.MooneyRivlinMaterial;
import artisynth.core.materials.MuscleMaterial;
import artisynth.core.femmodels.FemModel.SurfaceRender;
import artisynth.core.mechmodels.AxialSpring;
import artisynth.core.mechmodels.BodyConnector;
import artisynth.core.mechmodels.Collidable;
import artisynth.core.mechmodels.CollisionBehavior;
import artisynth.core.mechmodels.FrameMarker;
import artisynth.core.mechmodels.MechSystemSolver.Integrator;
import artisynth.core.mechmodels.MultiPointMuscle;
import artisynth.core.mechmodels.MultiPointSpring;
import artisynth.core.mechmodels.Muscle;
import artisynth.core.mechmodels.MuscleExciter;
import artisynth.core.mechmodels.Particle;
import artisynth.core.mechmodels.PlanarConnector;
import artisynth.core.mechmodels.RigidBody;
import artisynth.core.mechmodels.RigidCylinder;
import artisynth.core.mechmodels.RigidEllipsoid;
import artisynth.core.mechmodels.Wrappable;
import artisynth.core.modelbase.ComponentUtils;
import artisynth.core.modelbase.ModelComponent;
import artisynth.core.renderables.ColorBar;
import artisynth.core.rl.Log;
import artisynth.core.mechmodels.CollisionBehavior.ColorMapType;
import artisynth.core.mechmodels.CollisionBehavior.Method;
import artisynth.core.mechmodels.CollisionManager;
import artisynth.core.mechmodels.CollisionManager.ColliderType;
import artisynth.core.mechmodels.ExcitationComponent;
import artisynth.core.util.AmiraLandmarkReader;
import artisynth.core.util.ArtisynthPath;
//import artisynth.models.fem_jaw.JawBaseModel;
//import artisynth.models.uwknee.ElasticFoundationForceBehavior;
import maspack.geometry.PolygonalMesh;
import maspack.matrix.AxisAngle;
import maspack.matrix.Point3d;
import maspack.matrix.RigidTransform3d;
import maspack.matrix.RotationMatrix3d;
import maspack.matrix.Vector3d;
import maspack.matrix.VectorNd;
import maspack.properties.PropertyList;
import maspack.render.RenderList;
import maspack.render.RenderProps;
import maspack.render.Renderer;
import maspack.render.Renderer.ColorInterpolation;
import maspack.render.Renderer.FaceStyle;
import maspack.render.Renderer.LineStyle;
import maspack.render.Renderer.PointStyle;
import maspack.render.Renderer.Shading;
import maspack.render.color.JetColorMap;
import maspack.util.DoubleInterval;
import maspack.util.NumberFormat;
import maspack.util.ReaderTokenizer;
import artisynth.core.util.ScalarRange;

public class JawFemModel extends JawBaseModel {
	boolean withDisc = true;

	public boolean debug = false; // set to true for debug printlns

	public String[] muscleExciterCategoryNames = {"singleExciters", "groupExciters", "bilateralExciters"};
	protected HashMap<String, ArrayList<MuscleExciter>> myMuscleExciterCategories = new HashMap<String, ArrayList<MuscleExciter>>();
	
	public static final String muscleListFilename = "muscleList.txt";

	public static final String wrappedMuscleListFilename = "wrappedMuscleList.txt";

	public static final String bodyListFilename = "bodyList.txt";

	public static final String muscleInfoFilename = "muscleInfo.txt";

	public static final String femListFilename = "femList.txt";

	public static ArrayList<String> wrappedMuscleList = new ArrayList<String>();

	public static final String muscleGroupInfoFilename = "muscleGroupsInfo.txt";

	protected ArrayList<JawBaseModel.BodyInfo> femInfoList = new ArrayList<JawBaseModel.BodyInfo>();

	protected ArrayList<Muscle> myMuscles = new ArrayList<Muscle>();

	protected HashMap<String, String> muscleGroupNames = new LinkedHashMap<String, String>();

	protected double tmjDiscWeight = 0.0006; // 6g

	protected RigidTransform3d amiraTranformation = new RigidTransform3d(new Vector3d(0, 0, 0),
			new RotationMatrix3d(new AxisAngle(new Vector3d(0.975111, -0.20221, 0.0909384), Math.toRadians(-9.54211))));

	protected static MooneyRivlinMaterial defaultMooneyRivlinMaterial = new MooneyRivlinMaterial(
			900000 / unitConversion, 900 / unitConversion, 0d, 0d, 0d, /* 90000000 */10 * 9000000 / unitConversion);
	
	protected double linearMaterialNu = 0.33;
	protected double linearMaterialE = 5000.0;

	protected double myParticleDamping = 40;

	protected double myStiffnessDamping = 10;

	protected static final double DEFAULT_E = 6 * 450000 / unitConversion;

	protected static final double DEFAULT_Thickness = 0.4; // mm

	protected static final double DEFAULT_Damping = 75;

	protected static final double DEFAULT_Nu = 0.49;

	protected boolean useMooneyRivlin = false;
	protected boolean useBiteConstraints = true;

	protected boolean useElasticFoundationContact = true;

	// protected static RigidTransform3d hyoid_translation = new RigidTransform3d
	// (new Vector3d(0, 4, -6),new AxisAngle());
	// protected static RigidTransform3d hyoid_translation = new RigidTransform3d
	// (new Vector3d(0, 4, -11),new AxisAngle());

	protected static RigidTransform3d hyoid_translation = new RigidTransform3d(new Vector3d(0, 7, -7), new AxisAngle());

	LigamentAxialMaterial capsule_ligament_material = new LigamentAxialMaterial(250000, 0, 50);

	protected static PropertyList myProps = new PropertyList(JawFemModel.class, JawBaseModel.class);

	public PropertyList getAllPropertyInfo() {
		return myProps;
	}

	private void setupRenderProps() {

		// Line Renderprops
		RenderProps props = createRenderProps();
		props.setLineRadius(2.0);
		props.setLineWidth(3);
		props.setLineStyle(Renderer.LineStyle.LINE);
		props.setLineColor(Color.WHITE);

		// Mesh RenderProps
		props.setShading(Renderer.Shading.SMOOTH);
		props.setFaceColor(new Color(1f, 0.8f, 0.6f));
		props.setFaceStyle(Renderer.FaceStyle.FRONT_AND_BACK);
		setRenderProps(props);

		// Spring Render Props
		RenderProps.setLineRadius(myAxialSprings, 2.0);
		RenderProps.setLineStyle(myAxialSprings, Renderer.LineStyle.SPINDLE);
		RenderProps.setLineColor(myAxialSprings, Color.WHITE);

		// Marker RenderProps
		frameMarkers().getRenderProps().setPointStyle(PointStyle.SPHERE);
		frameMarkers().getRenderProps().setPointSize(1);
		frameMarkers().getRenderProps().setPointColor(Color.PINK);
	}

	// read list of FEM models
	private ArrayList<JawBaseModel.BodyInfo> readFemInfoList(String filename) throws IOException {
		ReaderTokenizer rtok = new ReaderTokenizer(new FileReader(filename));
		rtok.wordChars(".");
		ArrayList<JawBaseModel.BodyInfo> bodyInfoList = new ArrayList<JawBaseModel.BodyInfo>();

		while (rtok.nextToken() != ReaderTokenizer.TT_EOF) {
			JawBaseModel.BodyInfo bi = new JawBaseModel.BodyInfo();
			bi.scan(rtok);
			bodyInfoList.add(bi);
		}
		return bodyInfoList;
	}

	// update intertia and center of mass for the new geometry
	public void setNewJawDynamicProps() {
		rigidBodies().get("jaw").setInertiaFromMass(0.2);
		// rigidBodies().get("jaw").setCenterOfMass(new Point3d(6.954, -61.147,
		// 44.602));
		rigidBodies().get("jaw").setRotaryDamping(100);
		rigidBodies().get("jaw").setFrameDamping(50);
	}

	public FemModel3d createAndAddFemBody(String name, String meshName) {
		FemModel3d model = (FemModel3d) get(name);

		if (model == null) {
			model = new FemModel3d();
			model.setName(name);
			addModel(model);

			if (meshName.compareTo("none") != 0) {
				FemFactory.createFromMesh(model, loadFemMesh(meshName, 1), 0); // use tetgen to create volume mesh
			}

			RenderProps.setVisible(model, true);
			model.setSurfaceRendering(SurfaceRender.Stress);
			model.setDensity(tmjDiscWeight / model.getVolume());
			model.getElements().getRenderProps().setVisible(false);
		}

		if (useMooneyRivlin) {
			setMooneyRivlinMaterial(model);
			setIntegrator(Integrator.FullBackwardEuler);
		}
		else
			setLinearMaterial(model);
		

		return model;
	}

	public PolygonalMesh loadFemMesh(String meshName, double scale) {
		String meshFilename = ArtisynthPath.getSrcRelativePath(JawFemModel.class, "geometry/" + meshName);
		PolygonalMesh mesh = new PolygonalMesh();

		try {
			mesh.read(new BufferedReader(new FileReader(meshFilename)));
		} catch (IOException e) {
			e.printStackTrace();
		}

		mesh.scale(scale);
		mesh.setFixed(true);
		mesh.transform(amiraTranformation);

		return mesh;
	}

	public void assembleFemModels() throws IOException {
		for (JawBaseModel.BodyInfo bodyInfo : femInfoList) {
			createAndAddFemBody(bodyInfo.name, bodyInfo.meshName);
		}
	}

	public void setMooneyRivlinMaterial(FemModel3d model) {
		model.setMaterial(defaultMooneyRivlinMaterial);
		model.setParticleDamping(myParticleDamping);
		model.setStiffnessDamping(myStiffnessDamping);
	}
	
	public void setLinearMaterial(FemModel3d model) {
		// Default material in ArtiSynth is linear		
		LinearMaterial linearMaterial = new LinearMaterial(linearMaterialE, linearMaterialNu);
		linearMaterial.setCorotated(true);
		model.setMaterial(linearMaterial);
		model.setParticleDamping(myParticleDamping);
		model.setStiffnessDamping(myStiffnessDamping);
	}

	/**
	 * used to translate the frames of each body to the center of the body and
	 * translate components accordingly
	 */
	public void translateFrame(RigidBody body) {
		Vector3d centroid = new Vector3d();
		body.getSurfaceMesh().computeCentroid(centroid);
		RigidTransform3d XComToBody = new RigidTransform3d();
		XComToBody.p.set(centroid);

		RigidTransform3d XBodyToWorld = new RigidTransform3d();
		body.getPose(XBodyToWorld);

		RigidTransform3d XComToWorld = new RigidTransform3d();
		XComToWorld.mul(XBodyToWorld, XComToBody);
		body.setPose(XComToWorld);

		RigidTransform3d XMeshToCom = new RigidTransform3d();
		if (body.getSurfaceMesh() != null) {
			PolygonalMesh mesh = body.getSurfaceMesh();
			XMeshToCom.invert(XComToWorld);
			mesh.transform(XMeshToCom);
			body.setSurfaceMesh(mesh, null);
		}

		if (body.getName().equals("hyoid")) {
			body.transformGeometry(hyoid_translation);
		}

		for (FrameMarker mrk : frameMarkers()) {
			if (mrk.getFrame() == body) {
				Point3d loc = new Point3d();
				mrk.getLocation(loc);
				loc.transform(XMeshToCom);
				mrk.setLocation(loc);
			}
		}

		for (BodyConnector con : bodyConnectors()) {
			if (con.getBodyA() == body) {
				con.transformGeometry(XComToWorld);
			}
		}
	}

	public void addCartilage() {
		CollisionBehavior behav1 = new CollisionBehavior(true, 0);
		CollisionBehavior behav2 = new CollisionBehavior(true, 0);
		CollisionBehavior behav3 = new CollisionBehavior(true, 0);
		CollisionBehavior behav4 = new CollisionBehavior(true, 0);

		behav1.setMethod(Method.VERTEX_PENETRATION_BILATERAL);
		behav2.setMethod(Method.VERTEX_PENETRATION_BILATERAL);
		behav3.setMethod(Method.VERTEX_PENETRATION_BILATERAL);
		behav4.setMethod(Method.VERTEX_PENETRATION_BILATERAL);

		if (useElasticFoundationContact == true) {
			ElasticFoundationForceBehavior EFContact = new ElasticFoundationForceBehavior(DEFAULT_E, DEFAULT_Nu,
					DEFAULT_Damping, DEFAULT_Thickness);
			behav1.setForceBehavior(EFContact);
			behav2.setForceBehavior(EFContact);
			behav3.setForceBehavior(EFContact);
			behav4.setForceBehavior(EFContact);

			getCollisionManager().setColliderType(ColliderType.AJL_CONTOUR);
		}

		behav1.setMethod(Method.DEFAULT);
		behav1.setName("mand_disc_right");
		setCollisionBehavior(rigidBodies().get("jaw"), (FemModel3d) models().get("disc_right"), behav1);

		behav2.setMethod(Method.DEFAULT);
		behav2.setName("skull_disc_right");
		setCollisionBehavior(rigidBodies().get("skull"), (FemModel3d) models().get("disc_right"), behav2);

		behav3.setMethod(Method.DEFAULT);
		behav3.setName("mand_disc_left");
		setCollisionBehavior(rigidBodies().get("jaw"), (FemModel3d) models().get("disc_left"), behav3);

		behav4.setMethod(Method.DEFAULT);
		behav4.setName("skull_disc_left");
		setCollisionBehavior(rigidBodies().get("skull"), (FemModel3d) models().get("disc_left"), behav4);

		// attach cartilage to respective bones
		attachFrame(rigidBodies().get("mandible_cartilage_right"), rigidBodies().get("jaw"));
		attachFrame(rigidBodies().get("mandible_cartilage_left"), rigidBodies().get("jaw"));
		attachFrame(rigidBodies().get("skull_cartilage_right"), rigidBodies().get("skull"));
		attachFrame(rigidBodies().get("skull_cartilage_left"), rigidBodies().get("skull"));

		rigidBodies().get("mandible_cartilage_right").setMass(0);
		rigidBodies().get("mandible_cartilage_left").setMass(0);
		rigidBodies().get("skull_cartilage_right").setMass(0);
		rigidBodies().get("skull_cartilage_left").setMass(0);
	}

	protected void createBiteConstraints() {
		Vector3d pCA = new Vector3d(-2.26103, -44.0217, 6.87158);
		RigidTransform3d XPW = new RigidTransform3d(pCA, new AxisAngle(new Vector3d(1, 0, 0), Math.toRadians(180)));
		PlanarConnector con = new PlanarConnector(rigidBodies().get("jaw"), pCA, XPW);
		con.setPlaneSize(20);
		con.getRenderProps().setAlpha(0.7);
		con.getRenderProps().setFaceColor(Color.GRAY);
		con.setUnilateral(true);
		con.setName("BiteICP");

		Vector3d pCA2 = new Vector3d(24.9041, -18.4052, 4.9116);
		RigidTransform3d XPW2 = new RigidTransform3d(pCA2, new AxisAngle(new Vector3d(1, 0, 0), Math.toRadians(180)));
		XPW2.mulRotation(new RotationMatrix3d(new AxisAngle(new Vector3d(0, 1, 0), Math.toRadians(-36))));
		XPW2.mulRotation(new RotationMatrix3d(new AxisAngle(new Vector3d(0, 0, 1), Math.toRadians(15))));
		PlanarConnector con2 = new PlanarConnector(rigidBodies().get("jaw"), pCA2, XPW2);
		con2.setPlaneSize(20);
		con2.getRenderProps().setAlpha(0.7);
		con2.getRenderProps().setFaceColor(Color.GRAY);
		con2.setUnilateral(true);
		con2.setName("Brux_M6");

		Vector3d pCA3 = new Vector3d(11.8622, -39.9166, 6.1264);
		RigidTransform3d XPW3 = new RigidTransform3d(pCA3, new AxisAngle(new Vector3d(1, 0, 0), Math.toRadians(180)));
		XPW3.mulRotation(new RotationMatrix3d(new AxisAngle(new Vector3d(0, 1, 0), Math.toRadians(-50))));
		XPW3.mulRotation(new RotationMatrix3d(new AxisAngle(new Vector3d(1, 0, 0), Math.toRadians(15))));
		/*** ABSTRACT ESB **/
		// XPW3.mulRotation (new RotationMatrix3d(new AxisAngle(new
		// Vector3d(0,1,0),Math.toRadians (-36))));
		// XPW3.mulRotation (new RotationMatrix3d(new AxisAngle(new
		// Vector3d(0,0,1),Math.toRadians (15))));
		/*** CHANGE BACK **/
		PlanarConnector con3 = new PlanarConnector(rigidBodies().get("jaw"), pCA3, XPW3);
		con3.setPlaneSize(20);
		con3.getRenderProps().setAlpha(0.7);
		con3.getRenderProps().setFaceColor(Color.GRAY);
		con3.setUnilateral(true);
		con3.setName("Brux_C");

		Vector3d pCA4 = new Vector3d(20.7678, -28.5475, 4.78484);
		RigidTransform3d XPW4 = new RigidTransform3d(pCA4, new AxisAngle(new Vector3d(1, 0, 0), Math.toRadians(180)));
		XPW4.mulRotation(new RotationMatrix3d(new AxisAngle(new Vector3d(0, 1, 0), Math.toRadians(-40))));
		PlanarConnector con4 = new PlanarConnector(rigidBodies().get("jaw"), pCA4, XPW4);
		con4.setPlaneSize(20);
		con4.getRenderProps().setAlpha(0.7);
		con4.getRenderProps().setFaceColor(Color.GRAY);
		con4.setUnilateral(true);
		con4.setName("Brux_PM5");

		Vector3d pCA5 = new Vector3d(17.0506, -34.5288, 4.7637);
		RigidTransform3d XPW5 = new RigidTransform3d(pCA5, new AxisAngle(new Vector3d(1, 0, 0), Math.toRadians(180)));
		XPW5.mulRotation(new RotationMatrix3d(new AxisAngle(new Vector3d(0, 1, 0), Math.toRadians(-46))));
		// XPW5.mulRotation (new RotationMatrix3d(new AxisAngle(new
		// Vector3d(1,0,0),Math.toRadians (-25))));
		PlanarConnector con5 = new PlanarConnector(rigidBodies().get("jaw"), pCA5, XPW5);
		con5.setPlaneSize(20);
		con5.getRenderProps().setAlpha(0.7);
		con5.getRenderProps().setFaceColor(Color.GRAY);
		con5.setUnilateral(true);
		con5.setName("Brux_PM4");

		addBodyConnector(con);
		addBodyConnector(con2);
		addBodyConnector(con3);
		addBodyConnector(con4);
		addBodyConnector(con5);

	}
	
	protected void addFrameMarkers() {
		// create framemarkers for contact points of constraints
		FrameMarker m1 = new FrameMarker(rigidBodies().get("skull"), new Point3d(2.4077531, -96.598413, -43.842046));
		FrameMarker m2 = new FrameMarker(rigidBodies().get("jaw"), new Point3d(2.3768318, -94.201781, -40.301746));
		FrameMarker m3 = new FrameMarker(rigidBodies().get("jaw"), new Point3d(16.27707, -89.304763, -40.534119));
		FrameMarker m4 = new FrameMarker(rigidBodies().get("jaw"), new Point3d(29.397112, -67.681713, -41.787386));
		FrameMarker m5 = new FrameMarker(rigidBodies().get("jaw"), new Point3d(25.260763, -77.824013, -41.914129));
		FrameMarker m6 = new FrameMarker(rigidBodies().get("jaw"), new Point3d(21.54352, -83.805296, -41.935241));
		m1.setName("skull");
		m2.setName("lowerincisor");
		m3.setName("c_r");
		m4.setName("m6_r");
		m5.setName("pm5_r");
		m6.setName("pm4_r");
		addFrameMarker(m1);
		addFrameMarker(m2);
		addFrameMarker(m3);
		addFrameMarker(m4);
		addFrameMarker(m5);
		addFrameMarker(m6);
	}

	protected void attachLigaments() throws IOException {

		FrameMarker m_ml_r = new FrameMarker(rigidBodies().get("mandible_cartilage_right"),
				new Point3d(0.291939, -4.14064, 2.56426));
		m_ml_r.setName("jaw_ligament_r");
		FrameMarker m_sl_r = new FrameMarker(rigidBodies().get("skull_cartilage_right"),
				new Point3d(0.448085, 4.52591, 2.58537));
		m_sl_r.setName("skull_ligament_r");

		// FrameMarker m_ml_l= new FrameMarker (rigidBodies ().get
		// ("mandible_cartilage_left"), new Point3d(-2.17999, -2.86218, 1.81019));
		FrameMarker m_ml_l = new FrameMarker(rigidBodies().get("mandible_cartilage_left"),
				new Point3d(1.70796, -3.90319, 1.19122));
		m_ml_l.setName("jaw_ligament_l");
		FrameMarker m_sl_l = new FrameMarker(rigidBodies().get("skull_cartilage_left"),
				new Point3d(1.09947, 6.27995, 0.589989));
		m_sl_l.setName("skull_ligament_l");

		FrameMarker lat_lig_l = new FrameMarker(rigidBodies().get("mandible_cartilage_left"),
				new Point3d(10.8907, -3.11902, 0.241905));
		lat_lig_l.setName("lat_caps_l");

		FrameMarker med_lig_l = new FrameMarker(rigidBodies().get("mandible_cartilage_left"),
				new Point3d(-11.0365, 2.3139, 3.08688));
		med_lig_l.setName("med_caps_l");

		FrameMarker lat_lig_r = new FrameMarker(rigidBodies().get("mandible_cartilage_right"),
				new Point3d(-10.7861, -1.52841, 1.46048));
		lat_lig_r.setName("lat_caps_r");

		FrameMarker med_lig_r = new FrameMarker(rigidBodies().get("mandible_cartilage_right"),
				new Point3d(10.3989, 2.79619, 2.22426));
		med_lig_r.setName("med_caps_r");

		addFrameMarker(m_ml_r);
		addFrameMarker(m_ml_l);
		addFrameMarker(m_sl_r);
		addFrameMarker(m_sl_l);
		addFrameMarker(lat_lig_l);
		addFrameMarker(med_lig_l);
		addFrameMarker(lat_lig_r);
		addFrameMarker(med_lig_r);

		ArrayList<Integer> attached_points = readIntList(
				ArtisynthPath.getSrcRelativePath(JawFemModel.class, "data" + "/attachment_left_ant.txt"));

		double size = 4;

		RigidCylinder cylinder = new RigidCylinder("anterior_ligament_wrapping_left", size, 3 * size, 0);
		cylinder.setDynamic(false);
		cylinder.setPose(new RigidTransform3d(new Vector3d(54.812, -6.8707, -4.7085),
				new AxisAngle(0.14312, 0.98485, -0.097955, Math.toRadians(95.844))));
		// cylinder.getRenderProps ().setFaceColor (Color.BLUE);
		cylinder.getRenderProps().setFaceStyle(FaceStyle.NONE);
		cylinder.getRenderProps().setLineColor(Color.BLUE);
		cylinder.getRenderProps().setDrawEdges(true);
		addRigidBody(cylinder);
		attachFrame(cylinder, rigidBodies().get("jaw"));
		createLigament(attached_points, (FemModel3d) models().get("disc_left"), m_ml_l, 4, cylinder);
		attached_points.clear();

		attached_points = readIntList(
				ArtisynthPath.getSrcRelativePath(JawFemModel.class, "data" + "/attachment_right_ant.txt"));
		RigidCylinder cylinder2 = new RigidCylinder("anterior_ligament_wrapping_right", size, 3 * size, 0);
		cylinder2.setDynamic(false);
		cylinder2.setPose(new RigidTransform3d(new Vector3d(-45.3936, -5.09499, -6.75523),
				new AxisAngle(-0.015408, 0.99489, 0.099805, Math.toRadians(83.846))));
		cylinder2.getRenderProps().setFaceColor(Color.BLUE);
		cylinder2.getRenderProps().setFaceStyle(FaceStyle.NONE);
		cylinder2.getRenderProps().setLineColor(Color.BLUE);
		cylinder2.getRenderProps().setDrawEdges(true);
		addRigidBody(cylinder2);
		attachFrame(cylinder2, rigidBodies().get("jaw"));
		createLigament(attached_points, (FemModel3d) models().get("disc_right"), m_ml_r, 4, cylinder2);
		attached_points.clear();

		attached_points = readIntList(
				ArtisynthPath.getSrcRelativePath(JawFemModel.class, "data" + "/attachment_right_skull.txt"));
		createLigament(attached_points, (FemModel3d) models().get("disc_right"), (m_sl_r), 7.5);
		attached_points.clear();

		attached_points = readIntList(
				ArtisynthPath.getSrcRelativePath(JawFemModel.class, "data" + "/attachment_left_skull.txt"));
		createLigament(attached_points, (FemModel3d) models().get("disc_left"), m_sl_l, 7.5);
		attached_points.clear();

		attached_points = readIntList(
				ArtisynthPath.getSrcRelativePath(JawFemModel.class, "data" + "/attachment_left_lat.txt"));
		createLigament(attached_points, (FemModel3d) models().get("disc_left"), lat_lig_l, 2.5);
		attached_points.clear();

		attached_points = readIntList(
				ArtisynthPath.getSrcRelativePath(JawFemModel.class, "data" + "/attachment_left_med.txt"));
		createLigament(attached_points, (FemModel3d) models().get("disc_left"), med_lig_l, 1.9);
		attached_points.clear();

		attached_points = readIntList(
				ArtisynthPath.getSrcRelativePath(JawFemModel.class, "data" + "/attachment_right_lat.txt"));
		createLigament(attached_points, (FemModel3d) models().get("disc_right"), lat_lig_r, 2.5);
		attached_points.clear();

		attached_points = readIntList(
				ArtisynthPath.getSrcRelativePath(JawFemModel.class, "data" + "/attachment_right_med.txt"));
		createLigament(attached_points, (FemModel3d) models().get("disc_right"), med_lig_r, 1.9);
		attached_points.clear();
	}

	public void createLigament(ArrayList<Integer> attached_points, FemModel3d model, FrameMarker m, double slack) {
		// create a particle at the middle of the FEM nodes where the force of the
		// ligament will be distributed and attach these nodes to the particle

		Point3d pos = new Point3d();
		for (int i = 0; i < attached_points.size(); i++) {
			pos.add(model.getNode(attached_points.get(i)).getPosition());
		}

		pos.x = pos.x / (attached_points.size());
		pos.y = pos.y / (attached_points.size());
		pos.z = pos.z / (attached_points.size());

		Particle p1 = new Particle();
		p1.setMass(0);
		p1.setPosition(pos);
		addParticle(p1);
		PointFem3dAttachment att = new PointFem3dAttachment(p1);
		att.setFromNodes(p1.getPosition(), collectNodes(model, attached_points));
		addAttachment(att);

		// create an axial spring between bone frame marker and particle
		AxialSpring as = new AxialSpring();
		as.setFirstPoint(p1);
		as.setSecondPoint(m);
		as.setMaterial(capsule_ligament_material);
		as.getRenderProps().setLineStyle(LineStyle.LINE);
		as.setRestLength(as.getLength() + slack);
		as.getRenderProps().setLineColor(Color.GREEN);
		as.getRenderProps().setLineStyle(LineStyle.CYLINDER);
		as.getRenderProps().setLineRadius(0.75);
		addAxialSpring(as);

		for (FemNode n : att.getNodes()) {
			RenderProps.setSphericalPoints(n, 0.2, Color.GREEN);
		}
	}

	public void createLigament(ArrayList<Integer> attached_points, FemModel3d model, FrameMarker m, double slack,
			RigidBody wrappingBody) {
		// create a particle at the middle of the FEM nodes where the force of the
		// ligament will be distributed and attach these nodes to the particle

		Point3d pos = new Point3d();
		for (int i = 0; i < attached_points.size(); i++) {
			pos.add(model.getNode(attached_points.get(i)).getPosition());
		}

		pos.x = pos.x / (attached_points.size());
		pos.y = pos.y / (attached_points.size());
		pos.z = pos.z / (attached_points.size());
		Particle p1 = new Particle();
		p1.setMass(0);
		// p1.setPosition (model.getNode (attached_points.get (Math.round
		// (attached_points.size ()/2))).getPosition());
		p1.setPosition(pos);
		addParticle(p1);
		PointFem3dAttachment att = new PointFem3dAttachment(p1);
		att.setFromNodes(p1.getPosition(), collectNodes(model, attached_points));
		addAttachment(att);

		// create an multipoint spring between bone frame marker and particle
		MultiPointSpring as = new MultiPointSpring();
		as.addPoint(m);
		as.setSegmentWrappable(30);
		as.addWrappable((Wrappable) wrappingBody);
		as.addPoint(p1);
		as.setMaterial(capsule_ligament_material);
		as.getRenderProps().setLineStyle(LineStyle.LINE);
		as.setRestLength(as.getLength() + slack);
		as.getRenderProps().setLineColor(Color.GREEN);
		as.getRenderProps().setLineStyle(LineStyle.CYLINDER);
		as.getRenderProps().setLineRadius(0.75);
		addMultiPointSpring(as);

		for (FemNode n : att.getNodes()) {
			RenderProps.setSphericalPoints(n, 0.2, Color.GREEN);
		}
	}

	private HashSet<FemNode3d> collectNodes(FemModel3d fem, ArrayList<Integer> nodeNums) {
		HashSet<FemNode3d> nodes = new LinkedHashSet<FemNode3d>();
		for (int i = 0; i < nodeNums.size(); i++) {
			FemNode3d e = fem.getNode(nodeNums.get(i));
			nodes.add(e);
		}
		return nodes;
	}

	private ArrayList<Integer> readIntList(String filename) throws IOException {
		ArrayList<Integer> stringList = new ArrayList<Integer>();
		ReaderTokenizer rtok = new ReaderTokenizer(new FileReader(filename));

		while (rtok.nextToken() != ReaderTokenizer.TT_EOF) {
			if (rtok.ttype != ReaderTokenizer.TT_NUMBER) {
				throw new IOException("readMarkerList Expecting number, got " + rtok.tokenName());
			}
			stringList.add((int) rtok.lval);
		}
		return stringList;
	}

	public void addWrappedMuscles(ArrayList<String> wrappedMuscleList, HashMap<String, ExcitationComponent> myMuscles) {
		HashMap<String, RigidBody> wrappingBodies = new LinkedHashMap<String, RigidBody>();
		/*
		 * as of now only the superior head of the lateral pterygoid has wrapping
		 * geometry wrapping bodies are created and only added if the related muscles in
		 * part of wrappedMuscleList
		 */
		double size = 10;
		double density = 150;
		RigidCylinder cylinder = new RigidCylinder("cylinder_rsp", size / 1.1, 2 * size, density, 50);
		cylinder.setPose(new RigidTransform3d(new Vector3d(-40.1763, -10.8551, 2.3),
				new AxisAngle(-0.12701, 0.98571, 0.11067, Math.toRadians(89.728))));
		cylinder.setDynamic(false);
		cylinder.getRenderProps().setFaceColor(Color.BLUE);
		cylinder.getRenderProps().setFaceColor(Color.BLUE);
		cylinder.getRenderProps().setFaceStyle(FaceStyle.NONE);
		cylinder.getRenderProps().setLineColor(Color.BLUE);
		cylinder.getRenderProps().setDrawEdges(true);

		wrappingBodies.put("rsp", cylinder);

		RigidCylinder cylinder2 = new RigidCylinder("cylinder_lsp", size / 1.1, 2 * size, density, 50);
		cylinder2.setDynamic(false);
		cylinder2.setPose(new RigidTransform3d(new Vector3d(49.0791, -14.5805, 4.64117),
				new AxisAngle(0.14726, 0.98318, -0.10801, Math.toRadians(90.734))));
		cylinder2.getRenderProps().setFaceColor(Color.BLUE);
		cylinder2.getRenderProps().setFaceColor(Color.BLUE);
		cylinder2.getRenderProps().setFaceStyle(FaceStyle.NONE);
		cylinder2.getRenderProps().setLineColor(Color.BLUE);
		cylinder2.getRenderProps().setDrawEdges(true);
		wrappingBodies.put("lsp", cylinder2);

		ArrayList<AxialSpring> wrappedMuscles = new ArrayList<AxialSpring>();

		for (int i = 0; i < wrappedMuscleList.size(); i++) {
			wrappedMuscles.add(axialSprings().get(wrappedMuscleList.get(i)));
			addRigidBody(wrappingBodies.get(wrappedMuscles.get(i).getName()));
		}

		/*
		 * sets up MultiPointMuscle using the same material properties that have been
		 * used for the respective axial spring muscle and deletes axial spring muscle +
		 * replaces it in myMuscles List
		 */
		for (int i = 0; i < wrappedMuscles.size(); i++) {
			MultiPointMuscle m = new MultiPointMuscle();
			m.addPoint(((Muscle) axialSprings().get(wrappedMuscleList.get(i))).getFirstPoint());
			m.setSegmentWrappable(20, new Point3d[] { new Point3d(0, 0, 0) });
			m.addWrappable((Wrappable) wrappingBodies.get(wrappedMuscleList.get(i)));
			m.addPoint(((Muscle) axialSprings().get(wrappedMuscleList.get(i))).getSecondPoint());
			m.updateWrapSegments();
			m.setMaterial(((Muscle) axialSprings().get(wrappedMuscleList.get(i))).getMaterial());
			String name = ((Muscle) axialSprings().get(wrappedMuscleList.get(i))).getName();

			double length = m.getLength();
			double optLength = ((AxialMuscleMaterial) m.getMaterial()).getOptLength();
			double maxLength = ((AxialMuscleMaterial) m.getMaterial()).getMaxLength();
			double maxOptRatio = (optLength != 0.0) ? maxLength / optLength : 1.0;
			((AxialMuscleMaterial) m.getMaterial()).setOptLength(length);
			((AxialMuscleMaterial) m.getMaterial()).setMaxLength(length * maxOptRatio);
			((AxialMuscleMaterial) m.getMaterial())
					.setMaxForce(((AxialMuscleMaterial) m.getMaterial()).getMaxForce() * 0.001);

			m.setName(name);
			m.setExcitationColor(Color.RED);
			m.getRenderProps().setLineStyle(LineStyle.CYLINDER);
			m.getRenderProps().setLineRadius(0.75);
			addMultiPointSpring(m);

			myMuscles.remove(name);
			myMuscles.put(name, m);
		}

		ComponentUtils.removeComponents(wrappedMuscles, null);
	}

	public void renderCollisionForces() {
		/*
		 * CollisionBehavior behav = getCollisionBehavior
		 * ((Collidable)rigidBodies().get("jaw"), (Collidable)models ().get
		 * ("disc_right")); //behav.setDrawContactForces (true); behav.setDrawColorMap
		 * (ColorMapType.CONTACT_PRESSURE); behav.getPenetrationDepthRange().setUpdating
		 * ( ScalarRange.Updating.AUTO_FIT);
		 * 
		 * behav = getCollisionBehavior ((Collidable)rigidBodies().get("jaw"),
		 * (Collidable)models ().get ("disc_left")); //behav.setDrawContactForces
		 * (true); behav.setDrawColorMap (ColorMapType.CONTACT_PRESSURE);
		 * behav.getPenetrationDepthRange().setUpdating (
		 * ScalarRange.Updating.AUTO_FIT);
		 */
		CollisionBehavior behav = getCollisionBehavior((Collidable) rigidBodies().get("skull"),
				(Collidable) models().get("disc_right"));
		// behav.setDrawContactForces (true);
		behav.setDrawColorMap(ColorMapType.CONTACT_PRESSURE);
		behav.getPenetrationDepthRange().setUpdating(ScalarRange.Updating.AUTO_FIT);

		behav = getCollisionBehavior((Collidable) rigidBodies().get("skull"), (Collidable) models().get("disc_left"));
		// behav.setDrawContactForces (true);
		behav.setDrawColorMap(ColorMapType.CONTACT_PRESSURE);
		behav.getPenetrationDepthRange().setUpdating(ScalarRange.Updating.AUTO_FIT);

		CollisionManager cm = getCollisionManager();
		RenderProps.setVisible(cm, true);
		cm.setContactForceLenScale(10);

		JetColorMap map = new JetColorMap();
		map.setColorArray(new Color[] {
				/*
				 * new Color(255, 204, 153), // no penetration createColor (255, 153, 102),
				 * createColor (255, 153, 0), createColor (255, 255, 0), createColor (204, 0,
				 * 0), createColor (255, 0, 0), // most penetration
				 */

				// new Color(0xFFCC99 ), // dark bone
				new Color(0x0000FF), // blue
				new Color(0x007FFF), // dark cyan
				new Color(0x00FFFF), // cyan
				new Color(0x7FFF7F), // dark green
				new Color(0xFFFF00), // yellow
				new Color(0xFF7F00), // orange
				new Color(0xFF0000), // red
				new Color(0x7F0000), // dark red
		});

		/*
		 * new Color(255, 204, 153), // no penetration createColor (255, 153, 102),
		 * createColor (255, 102, 51), createColor (255, 51, 0), createColor (204, 0,
		 * 0), createColor (255, 0, 0), // most penetration
		 */
		// cm.getRenderProps ().setShading (Shading.SMOOTH);
		// cm.setColorMap (map);

		/*
		 * ColorBar cb = new ColorBar (map); cb.populateLabels (// in mm 0, 3, 5, new
		 * NumberFormat ("%4.2f")); addRenderable (cb);
		 */
	}

	// Creates and returns a ColorBar renderable object
	public ColorBar createColorBar() {
		ColorBar cbar = new ColorBar();
		cbar.setName("colorBar");
		cbar.setNumberFormat("%.2f"); // 2 decimal places
		cbar.populateLabels(0.0, 1.0, 10); // Start with range [0,1], 10 ticks
		cbar.setLocation(-100, 0.1, 20, 0.8);
		cbar.setTextColor(Color.WHITE);
		addRenderable(cbar); // add to root model's renderables
		return cbar;
	}

	public JawFemModel(String name, boolean withDisc) throws IOException {
		super();
		this.setName(name);
		this.withDisc = withDisc;
		setGravity(0, 0, -gravityVal * unitConversion);

		JawBaseModel.muscleList = readStringList(
				ArtisynthPath.getSrcRelativePath(JawFemModel.class, "geometry/" + muscleListFilename));
		JawBaseModel.bodyInfoList = readBodyInfoList(
				ArtisynthPath.getSrcRelativePath(JawFemModel.class, "geometry/" + bodyListFilename));
		femInfoList = readFemInfoList(
				ArtisynthPath.getSrcRelativePath(JawFemModel.class, "geometry/" + femListFilename));

		JawBaseModel.muscleInfo = readMuscleInfo(
				ArtisynthPath.getSrcRelativePath(JawFemModel.class, "geometry/" + muscleInfoFilename));
		JawBaseModel.muscleGroupInfo = readMuscleGroupsInfo(
				ArtisynthPath.getSrcRelativePath(JawFemModel.class, "geometry/" + muscleGroupInfoFilename));
		ArrayList<RigidBody> bodies = JawBaseModel.assembleRigidBodies(bodyInfoList, amiraTranformation,
				ArtisynthPath.getSrcRelativePath(JawFemModel.class, ""));

		for (RigidBody body : bodies) {
			if (body.getName().contains("cartilage") && this.withDisc == false)
				continue;
			Log.log(body.getName());
			addRigidBody(body);
		}

		setNewJawDynamicProps();

		ArrayList<FrameMarker> markers = JawBaseModel.assembleMarkers(muscleList, muscleInfo, myRigidBodies,
				amiraTranformation, ArtisynthPath.getSrcRelativePath(JawFemModel.class, ""));
		HashMap<String, FrameMarker> myMarkerInfo = new LinkedHashMap<String, FrameMarker>();
		for (FrameMarker marker : markers) {
			addFrameMarker(marker);
			myMarkerInfo.put(marker.getName(), marker);
		}

		if (useBiteConstraints)
			createBiteConstraints();
		addFrameMarkers();
		
		ArrayList<Muscle> myAssembledMuscles = JawBaseModel.assembleandreturnMuscles();
		ArrayList<Muscle> myAttachedMuscles = JawBaseModel.attachMuscles(muscleList, muscleInfo, myMarkerInfo,
				myAssembledMuscles);
		HashMap<String, ExcitationComponent> myMuscles = new LinkedHashMap<String, ExcitationComponent>();

		for (Muscle muscle : myAttachedMuscles) {
			muscle.setExcitationColor(Color.RED);
			muscle.setMaxColoredExcitation(1);
			addAxialSpring(muscle);
			myMuscles.put(muscle.getName(), muscle);
		}

		wrappedMuscleList = readStringList(
				ArtisynthPath.getSrcRelativePath(JawFemModel.class, "geometry/" + wrappedMuscleListFilename));
		// addWrappedMuscles(wrappedMuscleList, myMuscles);

		if (withDisc)
			assembleFemModels();

		for (RigidBody body : bodies) {
			translateFrame(body);
		}

		closerMuscleList = JawBaseModel.createMuscleList(
				readStringList(
						ArtisynthPath.getSrcRelativePath(JawFemModel.class, "geometry/" + "closerMuscleList.txt")),
				muscleInfo, myAttachedMuscles);

		addExciters(myMuscles);
		
		JawBaseModel.updateMuscleLengthProps(myAttachedMuscles);

		if (withDisc) {
			addCartilage();
			attachLigaments();
			// renderCollisionForces();

			// Create a colorbar
			ColorBar cbar = new ColorBar();
			cbar.setName("colorBar");
			cbar.setColorMap(((FemModel3d) models().get(0)).getColorMap());
			// addRenderable(cbar);
		} else {
			addInterBoneCollision();
		}

		setupRenderProps();
	}
	
	public void addExciters(HashMap<String, ExcitationComponent> myMuscles) {
		// the following line doesn't work!
		// assembleBilateralExcitors(muscleList, muscleInfo, myMuscles,
		// muscleAbbreviations);

		// add individual excitors
		System.out.println("Adding individual exciters");
		ArrayList<MuscleExciter> singleExciters = assembleIndividualExciters(myMuscles, getMuscleExciters());
		for (MuscleExciter exciter : singleExciters) {
			addMuscleExciter(exciter);
		}
		myMuscleExciterCategories.put("singleExciters", singleExciters);
		System.out.println("#excitors " + getMuscleExciters().size());

		// add group exciters
		System.out.println("Adding group exciters");
		ArrayList<MuscleExciter> groupExciters = new ArrayList<MuscleExciter>();
		for (MuscleGroupInfo info : muscleGroupInfo) {
			System.out.println("MuscleGroupInfo" + info.fullName + " " + info.name);
			ArrayList<MuscleExciter> exciters = assembleMuscleGroups(info, myMuscles, getMuscleExciters(),
					muscleAbbreviations);
			for (MuscleExciter mex : exciters) {
				if (myExciterList.get(mex.getName()) == null) {
					addMuscleExciter(mex);
					groupExciters.add(mex);
				}
			}
		}
		myMuscleExciterCategories.put("groupExciters", groupExciters);
		System.out.println("#excitors " + getMuscleExciters().size());

		// add bilateral exciters
		System.out.println("Adding bilateral exciters");
		ArrayList<MuscleExciter> bilateralExciters = assemblebilateralMuscleGroups(muscleGroupInfo,
				getMuscleExciters(), muscleAbbreviations);
		for (MuscleExciter exciter : bilateralExciters) {
			addMuscleExciter(exciter);
		}
		myMuscleExciterCategories.put("bilateralExciters", bilateralExciters);
		System.out.println("#excitors " + getMuscleExciters().size());
	}

	public void addInterBoneCollision() {
		CollisionBehavior behav1 = new CollisionBehavior(true, 0);

		// TODO: try VERTEX_EDGE_PENETRATION VERTEX_PENETRATION_BILATERAL
		behav1.setMethod(Method.VERTEX_EDGE_PENETRATION);

		if (useElasticFoundationContact == true) {
			ElasticFoundationForceBehavior EFContact = new ElasticFoundationForceBehavior(DEFAULT_E, DEFAULT_Nu,
					DEFAULT_Damping, DEFAULT_Thickness);
			behav1.setForceBehavior(EFContact);
			getCollisionManager().setColliderType(ColliderType.AJL_CONTOUR);
		}

		behav1.setMethod(Method.DEFAULT);
		behav1.setName("mand_skull");
		setCollisionBehavior(rigidBodies().get("jaw"), rigidBodies().get("skull"), behav1);
	}

}

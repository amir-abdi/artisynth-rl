package artisynth.models.rl.jaw;

import java.util.ArrayList;

import artisynth.core.mechmodels.CollisionResponse;
import artisynth.core.mechmodels.ContactForceBehavior;
import artisynth.core.mechmodels.ContactPoint;
import artisynth.core.mechmodels.CollisionHandler;
import maspack.collision.*;
import maspack.geometry.*;
import maspack.matrix.Vector3d;

/**
 * Implementation of non-linear elastic foundation compliance model from: Bei,
 * Y., & Fregly, B. J. (2004). Multibody dynamic simulation of knee contact
 * mechanics. Medical engineering & physics, 26(9), 777-789.
 * 
 * @author stavness
 */
public class ElasticFoundationForceBehavior implements ContactForceBehavior {

	double tol = 1e-10; // tolerance on distance < thickness
	double myPoissonRatio;
	double myYoungsModulus;
	double myThickness;
	double myDamping;
	double K;
	CollisionResponse response = null;

	public ElasticFoundationForceBehavior(double youngsModulus, double poissionRatio, double damping,
			double thickness) {
//      handler = h;
		myThickness = thickness;
		myYoungsModulus = youngsModulus;
		myDamping = damping;
		myPoissonRatio = poissionRatio;
		updateStiffness();
	}

	public void updateStiffness() {
		K = -(1 - myPoissonRatio) * myYoungsModulus / ((1 + myPoissonRatio) * (1 - 2 * myPoissonRatio));
	}

	public void setResponse(CollisionResponse resp) {
		response = resp;
	}

	@Override
	public void computeResponse(double[] fres, double dist, ContactPoint cpnt1, ContactPoint cpnt2, Vector3d normal,
			double area) {

		// XXX assume cpnt1 is always interpenetrating vertex
		// double area = 0.001d;

		if (area == -1) {
			System.err.println("EFContact: no region supplied; must use AJL_CONTOUR collisions");
		}

		if (myThickness + dist < tol) { // XXX how best to handle penetration larger than thickness?
			dist = tol - myThickness;
		}

		fres[0] = -K * Math.log(1 + dist / myThickness) * area; // force (distance is negative)
		fres[1] = -(myThickness + dist) / (K * area); // compliance
		fres[2] = myDamping;
	}

	public double getPoissonRatio() {
		return myPoissonRatio;
	}

	public void setPoissonRatio(double poissionRatio) {
		myPoissonRatio = poissionRatio;
		updateStiffness();
	}

	public double getYoungsModulus() {
		return myYoungsModulus;
	}

	public void setYoungsModulus(double youngsModulus) {
		this.myYoungsModulus = youngsModulus;
		updateStiffness();
	}

	public double getThickness() {
		return myThickness;
	}

	public void setThickness(double thickness) {
		this.myThickness = thickness;
	}

	public double getDamping() {
		return myDamping;
	}

	public void setDamping(double damping) {
		this.myDamping = damping;
	}

}

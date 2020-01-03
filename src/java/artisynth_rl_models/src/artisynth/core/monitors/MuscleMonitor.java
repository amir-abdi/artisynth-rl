package artisynth.core.monitors;

import artisynth.core.materials.AxialMuscleMaterial;
import artisynth.core.materials.MasoudMillardLAM;
import artisynth.core.mechmodels.Muscle;
import maspack.matrix.Vector3d;

public class MuscleMonitor extends RlMonitor {

	private Muscle myMuscle = new Muscle();


	public MuscleMonitor(Muscle as) {
		myMuscle = as;
	}

	public Muscle getMyMuscle() {
		return myMuscle;
	}

	public void setMyMuscle(Muscle myMuscle) {
		this.myMuscle = myMuscle;		
	}

	@Override
	public void apply(double t0, double t1) {
		if (isWrite((int)t0)) {
			double E = myMuscle.getNetExcitation();
			Vector3d PF = myMuscle.getPassiveForce();
			Vector3d F = myMuscle.getForce();
			double Fnorm = myMuscle.getForceNorm();
			double PFnorm = myMuscle.getPassiveForceNorm();
			double l = myMuscle.getLength();
			double ldot = myMuscle.getLengthDot();
			double lnod = myMuscle.getRestLength();
			AxialMuscleMaterial mat = (AxialMuscleMaterial) myMuscle
					.getMaterial(); 
			
			double optLength = mat.getOptLength();
			double tendonRatio = mat.getTendonRatio();
			double fiberLen = l - optLength * tendonRatio;
			double normFiberLength = fiberLen / optLength;
			normFiberLength = ((MasoudMillardLAM) mat).getNormFiberLen();

			out.println(t0 + " 2 " + E + " 4 " + Fnorm * 1000 + " 6 "
					+ PFnorm * 1000 + " 8 " + l + " 10 " + fiberLen + " 12 "
					+ optLength + " 14 " + normFiberLength + " 16 " + ldot
					+ " 18 " + F + " 22 " + PF + " 26 " + lnod);
		}
	}
}
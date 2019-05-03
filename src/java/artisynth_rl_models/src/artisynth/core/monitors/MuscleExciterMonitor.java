package artisynth.core.monitors;

import artisynth.core.mechmodels.MuscleExciter;

public class MuscleExciterMonitor extends RlMonitor {

	private MuscleExciter mymex = new MuscleExciter();

	public MuscleExciterMonitor(MuscleExciter mex) {
		mymex = mex;
	}

	public MuscleExciter getMymex() {
		return mymex;
	}

	public void setMymex(MuscleExciter mex) {
		this.mymex = mex;
	}

	@Override
	public void apply(double t0, double t1) {
		if (isWrite((int) t0)) {
			double E = mymex.getExcitation();
			out.println((int) t0 + "," + E);
		}
	}
}
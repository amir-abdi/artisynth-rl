package artisynth.core.monitors;

import artisynth.core.inverse.TargetPoint;

public class TargetPointMonitor extends RlMonitor {

	private TargetPoint myTargetPoint = new TargetPoint();

	public TargetPointMonitor(TargetPoint fm) {
		myTargetPoint = fm;
	}

	public TargetPoint getMyTargetPoint() {
		return myTargetPoint;
	}

	public void setMyTargetPoint(TargetPoint myFrameMarker) {
		this.myTargetPoint = myFrameMarker;
	}

	@Override
	public void apply(double t0, double t1) {
		if (isWrite((int) t0)) {
			out.println((int) t0 + "," + myTargetPoint.getPosition().x + "," + myTargetPoint.getPosition().y + ","
					+ myTargetPoint.getPosition().z);
		}
	}
}
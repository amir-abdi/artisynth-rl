package artisynth.core.monitors;

import artisynth.core.mechmodels.FrameMarker;

public class FrameMarkerMonitor extends RlMonitor {

	public FrameMarker myFrameMarker = new FrameMarker();

	public FrameMarkerMonitor(FrameMarker fm) {
		myFrameMarker = fm;
	}

	public FrameMarker getMyFrameMarker() {
		return myFrameMarker;
	}

	public void setMyFrameMarker(FrameMarker myFrameMarker) {
		this.myFrameMarker = myFrameMarker;
	}

	@Override
	public void apply(double t0, double t1) {
		if (isWrite((int) t0)) {
			out.println((int) t0 + "," + myFrameMarker.getPosition().x + "," + myFrameMarker.getPosition().y + ","
					+ myFrameMarker.getPosition().z);
		}
	}
}
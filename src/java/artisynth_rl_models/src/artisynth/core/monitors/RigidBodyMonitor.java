package artisynth.core.monitors;

import artisynth.core.mechmodels.RigidBody;
import maspack.matrix.AxisAngle;

public class RigidBodyMonitor extends RlMonitor {

	private RigidBody myRigidBody = new RigidBody();

	public RigidBodyMonitor(RigidBody rb) {
		myRigidBody = rb;
	}

	public RigidBody getMyRigidBody() {
		return myRigidBody;
	}

	public void setMyRigidBody(RigidBody myRigidBody) {
		this.myRigidBody = myRigidBody;
	}

	@Override
	public void apply(double t0, double t1) {
		if (isWrite((int) t0)) {
			AxisAngle a = new AxisAngle(myRigidBody.getOrientation());
			out.println((int) t0 + "," + a.axis.x + "," + a.axis.y + "," + a.axis.z + "," + a.angle / Math.PI * 180.0);
		}
	}
}
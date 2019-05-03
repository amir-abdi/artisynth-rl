package artisynth.core.rl;

import java.util.*;

import com.google.gson.annotations.Expose;

public class RlComponent {
	
	@Expose(serialize = false)
	private transient String name;
	
	// list of 3 doubles (vx, vy, vz)
	private ArrayList<Double> velocity;

	// list of 3 doubles (wx, wy, wz)
	private ArrayList<Double> angularVelocity;

	// list of 3 doubles for position (x, y, z)
	private ArrayList<Double> position;

	// list of 4 doubles for quaternion (s, q0, q1, q2)
	private ArrayList<Double> orientation;	

	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public List<Double> getVelocity() {
		return velocity;
	}

	public List<Double> getAngularVelocity() {
		return angularVelocity;
	}

	public List<Double> getPosition() {
		return position;
	}

	public List<Double> getOrientation() {
		return orientation;
	}

	public void setVelocity(ArrayList<Double> v) {
		assert v.size() == 3;
		velocity = new ArrayList<Double>(v.size());
		for (int i = 0; i < v.size(); ++i) {
			velocity.add(v.get(i));
		}
	}	

	public void setAngularVelocity(ArrayList<Double> v) {
		assert v.size() == 3;
		angularVelocity = new ArrayList<Double>(v.size());
		for (int i = 0; i < v.size(); ++i) {
			angularVelocity.add(v.get(i));
		}
	}

	public void setOrientation(ArrayList<Double> v) {
		assert v.size() == 4;
		orientation = new ArrayList<Double>(v.size());
		for (int i = 0; i < v.size(); ++i) {
			orientation.add(v.get(i));
		}
	}

	public void setPosition(ArrayList<Double> v) {
		assert v.size() == 3;
		position = new ArrayList<Double>(v.size());
		for (int i = 0; i < v.size(); ++i) {
			position.add(v.get(i));
		}
	}
	
	
	
	public void setVelocity(double[] v) {
		assert v.length == 3;
		velocity = new ArrayList<Double>(v.length);		
		for (int i = 0; i < v.length; ++i) {
			velocity.add(v[i]);
		}
	}
	
	public void setAngularVelocity(double[] v) {
		assert v.length == 3;
		angularVelocity = new ArrayList<Double>(v.length);		
		for (int i = 0; i < v.length; ++i) {
			angularVelocity.add(v[i]);
		}
	}
	
	public void setOrientation(double[] v) {
		assert v.length == 4;
		orientation = new ArrayList<Double>(v.length);		
		for (int i = 0; i < v.length; ++i) {
			orientation.add(v[i]);
		}
	}	
	
	public void setPosition(double[] v) {
		assert v.length == 3;
		position = new ArrayList<Double>(v.length);		
		for (int i = 0; i < v.length; ++i) {
			position.add(v[i]);
		}
	}
	
	public void setTwist(double[] v) {
		assert v.length == 6;
		
		velocity = new ArrayList<Double>(3);
		for (int i = 0; i < 3; ++i) {
			velocity.add(v[i]);
		}
		
		angularVelocity = new ArrayList<Double>(3);
		for (int i = 0; i < 3; ++i) {
			angularVelocity.add(v[i+3]);
		}
	}
}

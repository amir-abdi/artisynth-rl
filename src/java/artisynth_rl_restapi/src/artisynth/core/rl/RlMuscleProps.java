package artisynth.core.rl;

import java.util.ArrayList;

import com.google.gson.annotations.SerializedName;

public class RlMuscleProps {
	@SerializedName("excitations")
	private ArrayList<Double> props;

	public void setProps(ArrayList<Double> p) {
		props = p;
	}
	
	public ArrayList<Double> getProps() {
		return props;
	}
}

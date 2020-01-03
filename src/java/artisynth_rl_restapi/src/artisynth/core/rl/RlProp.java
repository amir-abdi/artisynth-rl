package artisynth.core.rl;

import java.util.*;

import com.google.gson.annotations.Expose;

public class RlProp {
	
	@Expose(serialize = false)
	private transient String name;
	
	private ArrayList<Double> prop;

	public RlProp() {	
	}
	
	public RlProp(String name) {
		this.name = name;
	}
	
	public List<Double> getProp() {
		return prop;
	}

	public void setProp(double[] p) {		
		prop = new ArrayList<Double>(p.length);		
		for (int i = 0; i < p.length; ++i) {
			prop.add(p[i]);
		}
	}
	
	public void setProp(ArrayList<Double> p) {		
		prop = new ArrayList<Double>(p.size());
		for (int i = 0; i < p.size(); ++i) {
			prop.add(p.get(i));
		}
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
}

package artisynth.core.rl;

import java.util.ArrayList;

public class RlState {
	private ArrayList<RlComponent> rlComponents;	
	
	public RlState() {
		rlComponents = new ArrayList<RlComponent>();
	}

	public ArrayList<RlComponent> getState() {
		return rlComponents;
	}

	public void setState(ArrayList<RlComponent> list) {
		// TODO Should we always deep copy? We are only reading... maybe switch all to
		// shallow copy
		rlComponents = new ArrayList<RlComponent>(list.size());
		for (int i = 0; i < list.size(); ++i) {
			rlComponents.set(i, list.get(i));
		}
	}
	
	public void addAll(ArrayList<RlComponent> list) {
		rlComponents.addAll(list);
	}
	
	public int numComponents() {
		return rlComponents.size();
	}
	
	public int size() {
		int size = 0;
		for (RlComponent rlComponent : rlComponents) {
			size += rlComponent.getPosition() != null ? rlComponent.getPosition().size() : 0;
			size += rlComponent.getOrientation() != null ? rlComponent.getOrientation().size() : 0;
			size += rlComponent.getAngularVelocity() != null ? rlComponent.getAngularVelocity().size() : 0;
			size += rlComponent.getVelocity() != null ? rlComponent.getVelocity().size() : 0;
		}
		return size;
	}

}

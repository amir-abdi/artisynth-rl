package artisynth.core.monitors;

import java.lang.*;
import java.util.ArrayList;

import org.python.antlr.op.Is;

import artisynth.core.modelbase.MonitorBase;
import artisynth.core.rl.Log;
import maspack.matrix.AxisAngle;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

public class AsyncDualBufferMonitor {
	Object buffer1, buffer2;
	boolean firstIsValid = true;
	
	public Object getProp() {
		if (firstIsValid) {
			return buffer1;
		} else {
			return  buffer2;
		}		
	}
	
	public void setProp(Object o) {
		if (o instanceof ArrayList<?>) {
			ArrayList<?> input_o = (ArrayList<?>)o;
			ArrayList<Object> new_array = new ArrayList<Object>(input_o.size());
			
			// Not doing a deep copy; because deep copy requires an object aware approach.
			// But, if the items in the ArrayList are primary datatypes, this should work fine.
			for (Object item : input_o) {
				new_array.add(item);
			}
			
			if (firstIsValid) {
				buffer2 = new_array;
				firstIsValid = false;
			} else {
				buffer1 = new_array;
				firstIsValid = true;
			}
			
		} else {
			Log.info(o.getClass());
			throw new NotImplementedException();
		}
	}

}

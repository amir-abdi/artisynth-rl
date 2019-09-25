package artisynth.models.rl.lumbarspine;

import maspack.function.Function1x3;
import artisynth.core.mechmodels.PointForce;
import artisynth.core.modelbase.ControllerBase;

public class PointForceController extends ControllerBase {

   private PointForce pf;
   private Function1x3 myFunction;

   public PointForceController (PointForce pointForce, Function1x3 func) {
      pf = pointForce;
      myFunction = func;
   }

   @Override
   public void apply (double t0, double t1) {
      pf.setForce (myFunction.eval (t0));
   }
}

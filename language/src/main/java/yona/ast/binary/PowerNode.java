package yona.ast.binary;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import yona.YonaException;
import yona.runtime.async.Promise;

@NodeInfo(shortName = "**")
public abstract class PowerNode extends BinaryOpNode {
  @Specialization
  public double doubles(double left, double right) {
    return Math.pow(left, right);
  }

  protected Promise promise(Object left, Object right) {
    Promise all = Promise.all(new Object[]{left, right}, this);
    return all.map(args -> {
      Object[] argValues = (Object[]) args;

      if (!argValues[0].getClass().equals(argValues[1].getClass())) {
        return YonaException.typeError(this, argValues);
      }

      if (argValues[0] instanceof Double && argValues[1] instanceof Double) {
        return Math.pow((double) argValues[0], (double) argValues[1]);
      } else {
        return YonaException.typeError(this, argValues);
      }
    }, this);
  }

  @Specialization
  public Promise leftPromise(Promise left, Object right) {
    return promise(left, right);
  }

  @Specialization
  public Promise rightPromise(Object left, Promise right) {
    return promise(left, right);
  }
}

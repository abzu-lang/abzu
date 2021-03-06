package yona.ast.builtin.modules;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import yona.ast.builtin.BuiltinNode;
import yona.runtime.exceptions.BadArgException;
import yona.runtime.stdlib.Builtins;
import yona.runtime.stdlib.ExportedFunction;

@BuiltinModuleInfo(packageParts = "java", moduleName = "Types")
public final class JavaTypesBuiltinModule implements BuiltinModule {
  @NodeInfo(shortName = "to_int")
  abstract static class ToIntBuiltin extends BuiltinNode {
    @Specialization
    @CompilerDirectives.TruffleBoundary
    public int toInt(long value) {
      if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
        return (int) value;
      } else {
        throw new BadArgException("value does not fit in Java Integer range: " + value, this);
      }
    }
  }

  @NodeInfo(shortName = "to_float")
  abstract static class ToFloatBuiltin extends BuiltinNode {
    @Specialization
    @CompilerDirectives.TruffleBoundary
    public float toInt(double value) {
      if (value >= Double.MIN_VALUE && value <= Double.MAX_VALUE) {
        return (float) value;
      } else {
        throw new BadArgException("value does not fit in Java Float range: " + value, this);
      }
    }
  }

  @Override
  public Builtins builtins() {
    return new Builtins(
        new ExportedFunction(JavaTypesBuiltinModuleFactory.ToIntBuiltinFactory.getInstance()),
        new ExportedFunction(JavaTypesBuiltinModuleFactory.ToFloatBuiltinFactory.getInstance())
    );
  }
}

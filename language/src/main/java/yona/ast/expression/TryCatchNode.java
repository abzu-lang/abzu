package yona.ast.expression;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeInfo;
import yona.YonaLanguage;
import yona.ast.ExpressionNode;
import yona.ast.pattern.MatchControlFlowException;
import yona.ast.pattern.PatternMatchable;
import yona.runtime.DependencyUtils;
import yona.runtime.async.Promise;
import yona.runtime.exceptions.NoMatchException;
import yona.runtime.exceptions.util.ExceptionUtil;

import java.util.Arrays;
import java.util.Objects;

@NodeInfo(shortName = "try")
public final class TryCatchNode extends ExpressionNode {
  @Child
  private ExpressionNode tryExpression;
  @Children
  private final PatternMatchable[] catchPatterns;

  public TryCatchNode(ExpressionNode tryExpression, PatternMatchable[] catchPatterns) {
    this.tryExpression = tryExpression;
    this.catchPatterns = catchPatterns;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TryCatchNode that = (TryCatchNode) o;
    return Objects.equals(tryExpression, that.tryExpression) &&
        Arrays.equals(catchPatterns, that.catchPatterns);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(tryExpression);
    result = 31 * result + Arrays.hashCode(catchPatterns);
    return result;
  }

  @Override
  public String toString() {
    return "TryCatchNode{" +
        "tryExpression=" + tryExpression +
        ", catchPatterns=" + Arrays.toString(catchPatterns) +
        '}';
  }

  @Override
  public void setIsTail(boolean isTail) {
    super.setIsTail(isTail);
    tryExpression.setIsTail(true);
    for (PatternMatchable patternMatchable : catchPatterns) {
      patternMatchable.setIsTail(isTail);
    }
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    try {
      Object value = tryExpression.executeGeneric(frame);

      if (value instanceof Promise) {
        Promise promise = (Promise) value;
        Object unwrappedValue = promise.unwrapWithError();

        if (unwrappedValue != null) {
          return execute(unwrappedValue, frame);
        } else {
          CompilerDirectives.transferToInterpreterAndInvalidate();
          MaterializedFrame materializedFrame = frame.materialize();
          return promise.map(
              (val) -> execute(val, materializedFrame),
              (val) -> execute(val, materializedFrame),
              this);
        }
      } else {
        return value;
      }
    } catch (Throwable t) {
      return execute(t, frame);
    }
  }

  @Override
  protected String[] requiredIdentifiers() {
    return DependencyUtils.catenateRequiredIdentifiersWith(tryExpression, catchPatterns);
  }

  @ExplodeLoop
  private Object execute(Object value, VirtualFrame frame) {
    CompilerAsserts.compilationConstant(catchPatterns.length);

    if (!(value instanceof Throwable)) {
      return value;
    }

    Throwable throwable = (Throwable) value;

    Object retValue = null;
    for (int i = 0; i < catchPatterns.length; i++) {
      try {
        retValue = catchPatterns[i].patternMatch(ExceptionUtil.throwableToTuple(throwable, lookupContextReference(YonaLanguage.class).get()), frame);
        break;
      } catch (MatchControlFlowException ex) {
        continue;
      }
    }

    if (retValue != null) {
      return retValue;
    } else {
      throw new NoMatchException(this);
    }
  }
}

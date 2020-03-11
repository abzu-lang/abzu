package yatta.ast.controlflow;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import yatta.YattaLanguage;
import yatta.ast.ExpressionNode;
import yatta.ast.call.InvokeNode;
import yatta.ast.expression.value.FQNNode;
import yatta.ast.pattern.PatternMatchable;

import java.util.Objects;

@NodeInfo
public final class PipeLeftNode extends ExpressionNode {
  @Child private InvokeNode invokeNode;

  public PipeLeftNode(YattaLanguage language, ExpressionNode leftExpression, ExpressionNode rightExpression, ExpressionNode[] moduleStack) {
    this.invokeNode = new InvokeNode(language, leftExpression, new ExpressionNode[]{rightExpression}, moduleStack);
  }

  @Override
  public String toString() {
    return "PipeLeftNode{" +
        "invokeNode=" + invokeNode +
        '}';
  }

  @Override
  public void setIsTail(boolean isTail) {
    super.setIsTail(isTail);
    invokeNode.setIsTail(isTail);
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return invokeNode.executeGeneric(frame);
  }
}

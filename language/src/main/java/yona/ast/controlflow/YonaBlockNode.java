package yona.ast.controlflow;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode;
import com.oracle.truffle.api.nodes.NodeInfo;
import yona.TypesGen;
import yona.ast.ExpressionNode;
import yona.runtime.DependencyUtils;

/**
 * A node that just executes a list of expressions and returns result of the last one.
 * Internal use only, there is no syntactical construct using this.
 */
@NodeInfo(shortName = "block", description = "The node implementing a source code block")
public final class YonaBlockNode extends ExpressionNode implements BlockNode.ElementExecutor<ExpressionNode> {
  @Child
  private BlockNode<ExpressionNode> block;

  public YonaBlockNode(ExpressionNode[] elements) {
    this.block = BlockNode.create(elements, this);
  }

  @Override
  public String toString() {
    return "YonaBlockNode{" +
        "block=" + block +
        '}';
  }

  @Override
  public void executeVoid(VirtualFrame frame, ExpressionNode node, int index, int arg) {
    node.executeGeneric(frame);
  }

  @Override
  public Object executeGeneric(VirtualFrame frame, ExpressionNode node, int index, int arg) {
    return node.executeGeneric(frame);
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    return TypesGen.ensureNotNull(block.executeGeneric(frame, 0));
  }

  @Override
  protected String[] requiredIdentifiers() {
    return DependencyUtils.catenateRequiredIdentifiers(block.getElements());
  }
}

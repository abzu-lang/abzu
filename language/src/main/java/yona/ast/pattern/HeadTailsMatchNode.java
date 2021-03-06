package yona.ast.pattern;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import yona.ast.AliasNode;
import yona.ast.ExpressionNode;
import yona.ast.expression.IdentifierNode;
import yona.ast.expression.NameAliasNode;
import yona.ast.expression.value.AnyValueNode;
import yona.ast.expression.value.EmptySequenceNode;
import yona.runtime.DependencyUtils;
import yona.runtime.Seq;

import java.util.Objects;

@NodeInfo(shortName = "headTailsMatch")
public final class HeadTailsMatchNode extends MatchNode {
  @Children
  public MatchNode headNodes[];
  @Child
  public ExpressionNode tailsNode;

  public HeadTailsMatchNode(MatchNode headNodes[], ExpressionNode tailsNode) {
    this.headNodes = headNodes;
    this.tailsNode = tailsNode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HeadTailsMatchNode that = (HeadTailsMatchNode) o;
    return Objects.equals(headNodes, that.headNodes) &&
        Objects.equals(tailsNode, that.tailsNode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(headNodes, tailsNode);
  }

  @Override
  public String toString() {
    return "HeadTailsMatchPatternNode{" +
        "headNodes=" + headNodes +
        ", tailsNode=" + tailsNode +
        '}';
  }

  @Override
  protected String[] requiredIdentifiers() {
    return tailsNode.getRequiredIdentifiers();
  }

  @Override
  public MatchResult match(Object value, VirtualFrame frame) {
    if (value instanceof Seq) {
      Seq sequence = (Seq) value;
      Seq aliases = Seq.EMPTY;

      if (headNodes.length > sequence.length()) {
        return MatchResult.FALSE;
      }

      if (sequence.length() > 0) {
        for (int i = 0; i < headNodes.length; i++) {
          MatchNode headNode = headNodes[i];
          MatchResult headMatches = headNode.match(sequence.first(this), frame);
          if (headMatches.isMatches()) {
            aliases = Seq.catenate(aliases, Seq.sequence((Object[]) headMatches.getAliases()));
            sequence = sequence.removeFirst(this);
          } else {
            return MatchResult.FALSE;
          }
        }

        // YonaParser.g4: tails : identifier | sequence | underscore | stringLiteral ;
        if (tailsNode instanceof IdentifierNode) {
          IdentifierNode identifierNode = (IdentifierNode) tailsNode;

          if (identifierNode.isBound(frame)) {
            Seq identifierValue = null;
            try {
              identifierValue = identifierNode.executeSequence(frame);
            } catch (UnexpectedResultException e) {
              return MatchResult.FALSE;
            }

            if (!Objects.equals(identifierValue, sequence)) {
              return MatchResult.FALSE;
            }
          } else {
            aliases = aliases.insertLast(new NameAliasNode(identifierNode.name(), new AnyValueNode(sequence)));
          }
        } else if (tailsNode instanceof EmptySequenceNode) {
          if (sequence.length() > 0) {
            return MatchResult.FALSE;
          }
        } else if (tailsNode instanceof UnderscoreMatchNode) {
          // nothing to match here
        } else { // otherSequence | stringLiteral
          Seq sequenceValue;
          try {
            sequenceValue = tailsNode.executeSequence(frame);
          } catch (UnexpectedResultException e) {
            return MatchResult.FALSE;
          }

          if (!Objects.equals(sequenceValue, sequence)) {
            return MatchResult.FALSE;
          }
        }

        aliases.foldLeft(null, (acc, alias) -> {
          ((AliasNode) alias).executeGeneric(frame);
          return null;
        });

        return MatchResult.TRUE;
      }
    }

    return MatchResult.FALSE;
  }

  @Override
  protected String[] providedIdentifiers() {
    return DependencyUtils.catenateProvidedIdentifiers(tailsNode.getRequiredIdentifiers(), headNodes);
  }
}

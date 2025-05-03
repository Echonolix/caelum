package tree_sitter.c.node

import tree_sitter.Node

public sealed interface ParenthesizedExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ParenthesizedExpressionNodeChildren {
            val n = createNode(node)
            if (n is ParenthesizedExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a ParenthesizedExpressionNodeChildren")
        }
    }
}

public class ParenthesizedExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode,
    AssignmentExpressionNodeChildren,
    DoStatementNodeChildren,
    IfStatementNodeChildren,
    PreprocElifNodeChildren,
    PreprocIfNodeChildren,
    SehExceptClauseNodeChildren,
    SwitchStatementNodeChildren,
    WhileStatementNodeChildren,
    AssignmentExpressionNodeLeft,
    PreprocElifNodeCondition,
    PreprocIfNodeCondition {
    public fun children(): ParenthesizedExpressionNodeChildren =
        ParenthesizedExpressionNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for parenthesized_expression")
        )
}

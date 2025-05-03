package tree_sitter.c.node

import tree_sitter.Node

public sealed interface SubscriptExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): SubscriptExpressionNodeChildren {
            val n = createNode(node)
            if (n is SubscriptExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a SubscriptExpressionNodeChildren")
        }
    }
}

public class SubscriptExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode,
    AssignmentExpressionNodeChildren,
    AssignmentExpressionNodeLeft {
    public val argument: ExpressionNode
        get() = ExpressionNode(
            `$node`.getChildByFieldName("argument") ?: error("required field argument is null")
        )

    public val index: ExpressionNode
        get() = ExpressionNode(
            `$node`.getChildByFieldName("index") ?: error("required field index is null")
        )

    public fun children(): List<SubscriptExpressionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            SubscriptExpressionNodeChildren(it)
        }
    }
}

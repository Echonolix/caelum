package tree_sitter.c.node

import tree_sitter.Node

public sealed interface CallExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): CallExpressionNodeChildren {
            val n = createNode(node)
            if (n is CallExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a CallExpressionNodeChildren")
        }
    }
}

public class CallExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode,
    FunctionDeclaratorNodeChildren,
    AssignmentExpressionNodeChildren,
    PreprocElifNodeChildren,
    PreprocIfNodeChildren,
    PreprocIncludeNodeChildren,
    AssignmentExpressionNodeLeft,
    PreprocElifNodeCondition,
    PreprocIfNodeCondition,
    PreprocIncludeNodePath {
    public val arguments: ArgumentListNode
        get() = ArgumentListNode(
            `$node`.getChildByFieldName("arguments") ?: error("required field arguments is null")
        )

    public val function: ExpressionNode
        get() = ExpressionNode(
            `$node`.getChildByFieldName("function") ?: error("required field function is null")
        )

    public fun children(): List<CallExpressionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            CallExpressionNodeChildren(it)
        }
    }
}

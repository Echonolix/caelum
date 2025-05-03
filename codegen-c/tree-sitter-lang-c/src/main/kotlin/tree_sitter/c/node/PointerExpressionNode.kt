package tree_sitter.c.node

import tree_sitter.Node

public sealed interface PointerExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PointerExpressionNodeChildren {
            val n = createNode(node)
            if (n is PointerExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a PointerExpressionNodeChildren")
        }
    }
}

public sealed interface PointerExpressionNodeOperator : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PointerExpressionNodeOperator {
            val n = createNode(node)
            if (n is PointerExpressionNodeOperator) {
                return n
            }
            throw IllegalArgumentException("Node is not a PointerExpressionNodeOperator")
        }
    }
}

public class PointerExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode,
    AssignmentExpressionNodeChildren,
    AssignmentExpressionNodeLeft {
    public val argument: ExpressionNode
        get() = ExpressionNode(
            `$node`.getChildByFieldName("argument") ?: error("required field argument is null")
        )

    public val `operator`: PointerExpressionNodeOperator
        get() = PointerExpressionNodeOperator(
            `$node`.getChildByFieldName("operator") ?: error("required field operator is null")
        )

    public fun children(): List<PointerExpressionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            PointerExpressionNodeChildren(it)
        }
    }
}

package tree_sitter.c.node

import tree_sitter.Node

public sealed interface UpdateExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): UpdateExpressionNodeChildren {
            val n = createNode(node)
            if (n is UpdateExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a UpdateExpressionNodeChildren")
        }
    }
}

public sealed interface UpdateExpressionNodeOperator : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): UpdateExpressionNodeOperator {
            val n = createNode(node)
            if (n is UpdateExpressionNodeOperator) {
                return n
            }
            throw IllegalArgumentException("Node is not a UpdateExpressionNodeOperator")
        }
    }
}

public class UpdateExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode {
    public val argument: ExpressionNode
        get() = ExpressionNode(
            `$node`.getChildByFieldName("argument") ?: error("required field argument is null")
        )

    public val `operator`: UpdateExpressionNodeOperator
        get() = UpdateExpressionNodeOperator(
            `$node`.getChildByFieldName("operator") ?: error("required field operator is null")
        )

    public fun children(): List<UpdateExpressionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            UpdateExpressionNodeChildren(it)
        }
    }
}

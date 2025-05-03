package tree_sitter.c.node

import tree_sitter.Node

public sealed interface UnaryExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): UnaryExpressionNodeChildren {
            val n = createNode(node)
            if (n is UnaryExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a UnaryExpressionNodeChildren")
        }
    }
}

public sealed interface UnaryExpressionNodeArgument : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): UnaryExpressionNodeArgument {
            val n = createNode(node)
            if (n is UnaryExpressionNodeArgument) {
                return n
            }
            throw IllegalArgumentException("Node is not a UnaryExpressionNodeArgument")
        }
    }
}

public sealed interface UnaryExpressionNodeOperator : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): UnaryExpressionNodeOperator {
            val n = createNode(node)
            if (n is UnaryExpressionNodeOperator) {
                return n
            }
            throw IllegalArgumentException("Node is not a UnaryExpressionNodeOperator")
        }
    }
}

public class UnaryExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode,
    PreprocElifNodeChildren,
    PreprocIfNodeChildren,
    PreprocElifNodeCondition,
    PreprocIfNodeCondition {
    public val argument: UnaryExpressionNodeArgument
        get() = UnaryExpressionNodeArgument(
            `$node`.getChildByFieldName("argument") ?: error("required field argument is null")
        )

    public val `operator`: UnaryExpressionNodeOperator
        get() = UnaryExpressionNodeOperator(
            `$node`.getChildByFieldName("operator") ?: error("required field operator is null")
        )

    public fun children(): List<UnaryExpressionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            UnaryExpressionNodeChildren(it)
        }
    }
}

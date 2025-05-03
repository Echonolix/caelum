package tree_sitter.c.node

import tree_sitter.Node

public sealed interface BinaryExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): BinaryExpressionNodeChildren {
            val n = createNode(node)
            if (n is BinaryExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a BinaryExpressionNodeChildren")
        }
    }
}

public sealed interface BinaryExpressionNodeLeft : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): BinaryExpressionNodeLeft {
            val n = createNode(node)
            if (n is BinaryExpressionNodeLeft) {
                return n
            }
            throw IllegalArgumentException("Node is not a BinaryExpressionNodeLeft")
        }
    }
}

public sealed interface BinaryExpressionNodeOperator : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): BinaryExpressionNodeOperator {
            val n = createNode(node)
            if (n is BinaryExpressionNodeOperator) {
                return n
            }
            throw IllegalArgumentException("Node is not a BinaryExpressionNodeOperator")
        }
    }
}

public sealed interface BinaryExpressionNodeRight : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): BinaryExpressionNodeRight {
            val n = createNode(node)
            if (n is BinaryExpressionNodeRight) {
                return n
            }
            throw IllegalArgumentException("Node is not a BinaryExpressionNodeRight")
        }
    }
}

public class BinaryExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode,
    PreprocElifNodeChildren,
    PreprocIfNodeChildren,
    PreprocElifNodeCondition,
    PreprocIfNodeCondition {
    public val left: BinaryExpressionNodeLeft
        get() = BinaryExpressionNodeLeft(
            `$node`.getChildByFieldName("left") ?: error("required field left is null")
        )

    public val `operator`: BinaryExpressionNodeOperator
        get() = BinaryExpressionNodeOperator(
            `$node`.getChildByFieldName("operator") ?: error("required field operator is null")
        )

    public val right: BinaryExpressionNodeRight
        get() = BinaryExpressionNodeRight(
            `$node`.getChildByFieldName("right") ?: error("required field right is null")
        )

    public fun children(): List<BinaryExpressionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            BinaryExpressionNodeChildren(it)
        }
    }
}

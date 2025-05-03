package tree_sitter.c.node

import tree_sitter.Node

public sealed interface AssignmentExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): AssignmentExpressionNodeChildren {
            val n = createNode(node)
            if (n is AssignmentExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a AssignmentExpressionNodeChildren")
        }
    }
}

public sealed interface AssignmentExpressionNodeLeft : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): AssignmentExpressionNodeLeft {
            val n = createNode(node)
            if (n is AssignmentExpressionNodeLeft) {
                return n
            }
            throw IllegalArgumentException("Node is not a AssignmentExpressionNodeLeft")
        }
    }
}

public sealed interface AssignmentExpressionNodeOperator : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): AssignmentExpressionNodeOperator {
            val n = createNode(node)
            if (n is AssignmentExpressionNodeOperator) {
                return n
            }
            throw IllegalArgumentException("Node is not a AssignmentExpressionNodeOperator")
        }
    }
}

public class AssignmentExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode {
    public val left: AssignmentExpressionNodeLeft
        get() = AssignmentExpressionNodeLeft(
            `$node`.getChildByFieldName("left") ?: error("required field left is null")
        )

    public val `operator`: AssignmentExpressionNodeOperator
        get() = AssignmentExpressionNodeOperator(
            `$node`.getChildByFieldName("operator") ?: error("required field operator is null")
        )

    public val right: ExpressionNode
        get() = ExpressionNode(
            `$node`.getChildByFieldName("right") ?: error("required field right is null")
        )

    public fun children(): List<AssignmentExpressionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            AssignmentExpressionNodeChildren(it)
        }
    }
}

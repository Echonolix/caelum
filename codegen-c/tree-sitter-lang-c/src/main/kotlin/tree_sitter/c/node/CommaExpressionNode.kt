package tree_sitter.c.node

import tree_sitter.Node

public sealed interface CommaExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): CommaExpressionNodeChildren {
            val n = createNode(node)
            if (n is CommaExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a CommaExpressionNodeChildren")
        }
    }
}

public sealed interface CommaExpressionNodeRight : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): CommaExpressionNodeRight {
            val n = createNode(node)
            if (n is CommaExpressionNodeRight) {
                return n
            }
            throw IllegalArgumentException("Node is not a CommaExpressionNodeRight")
        }
    }
}

public class CommaExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionStatementNodeChildren,
    ParenthesizedExpressionNodeChildren,
    ReturnStatementNodeChildren,
    CommaExpressionNodeChildren,
    ConditionalExpressionNodeChildren,
    ForStatementNodeChildren,
    CommaExpressionNodeRight,
    ConditionalExpressionNodeConsequence,
    ForStatementNodeCondition,
    ForStatementNodeInitializer,
    ForStatementNodeUpdate {
    public val left: ExpressionNode
        get() = ExpressionNode(
            `$node`.getChildByFieldName("left") ?: error("required field left is null")
        )

    public val right: CommaExpressionNodeRight
        get() = CommaExpressionNodeRight(
            `$node`.getChildByFieldName("right") ?: error("required field right is null")
        )

    public fun children(): List<CommaExpressionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            CommaExpressionNodeChildren(it)
        }
    }
}

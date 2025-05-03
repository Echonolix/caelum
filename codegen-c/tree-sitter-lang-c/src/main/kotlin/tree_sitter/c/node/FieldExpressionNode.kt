package tree_sitter.c.node

import tree_sitter.Node

public sealed interface FieldExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): FieldExpressionNodeChildren {
            val n = createNode(node)
            if (n is FieldExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a FieldExpressionNodeChildren")
        }
    }
}

public sealed interface FieldExpressionNodeOperator : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): FieldExpressionNodeOperator {
            val n = createNode(node)
            if (n is FieldExpressionNodeOperator) {
                return n
            }
            throw IllegalArgumentException("Node is not a FieldExpressionNodeOperator")
        }
    }
}

public class FieldExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode,
    AssignmentExpressionNodeChildren,
    AssignmentExpressionNodeLeft {
    public val argument: ExpressionNode
        get() = ExpressionNode(
            `$node`.getChildByFieldName("argument") ?: error("required field argument is null")
        )

    public val `field`: FieldIdentifierNode
        get() = FieldIdentifierNode(
            `$node`.getChildByFieldName("field") ?: error("required field field is null")
        )

    public val `operator`: FieldExpressionNodeOperator
        get() = FieldExpressionNodeOperator(
            `$node`.getChildByFieldName("operator") ?: error("required field operator is null")
        )

    public fun children(): List<FieldExpressionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            FieldExpressionNodeChildren(it)
        }
    }
}

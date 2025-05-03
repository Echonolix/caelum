package tree_sitter.c.node

import tree_sitter.Node

public sealed interface CastExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): CastExpressionNodeChildren {
            val n = createNode(node)
            if (n is CastExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a CastExpressionNodeChildren")
        }
    }
}

public class CastExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode {
    public val type: TypeDescriptorNode
        get() = TypeDescriptorNode(
            `$node`.getChildByFieldName("type") ?: error("required field type is null")
        )

    public val `value`: ExpressionNode
        get() = ExpressionNode(
            `$node`.getChildByFieldName("value") ?: error("required field value is null")
        )

    public fun children(): List<CastExpressionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            CastExpressionNodeChildren(it)
        }
    }
}

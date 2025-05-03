package tree_sitter.c.node

import tree_sitter.Node

public sealed interface OffsetofExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): OffsetofExpressionNodeChildren {
            val n = createNode(node)
            if (n is OffsetofExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a OffsetofExpressionNodeChildren")
        }
    }
}

public class OffsetofExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode {
    public val member: FieldIdentifierNode
        get() = FieldIdentifierNode(
            `$node`.getChildByFieldName("member") ?: error("required field member is null")
        )

    public val type: TypeDescriptorNode
        get() = TypeDescriptorNode(
            `$node`.getChildByFieldName("type") ?: error("required field type is null")
        )

    public fun children(): List<OffsetofExpressionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            OffsetofExpressionNodeChildren(it)
        }
    }
}

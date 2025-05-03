package tree_sitter.c.node

import tree_sitter.Node

public sealed interface SizeofExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): SizeofExpressionNodeChildren {
            val n = createNode(node)
            if (n is SizeofExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a SizeofExpressionNodeChildren")
        }
    }
}

public class SizeofExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode {
    public val type: TypeDescriptorNode?
        get() = (`$node`.getChildByFieldName("type"))?.let { TypeDescriptorNode(it) }

    public val `value`: ExpressionNode?
        get() = (`$node`.getChildByFieldName("value"))?.let { ExpressionNode(it) }

    public fun children(): List<SizeofExpressionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            SizeofExpressionNodeChildren(it)
        }
    }
}

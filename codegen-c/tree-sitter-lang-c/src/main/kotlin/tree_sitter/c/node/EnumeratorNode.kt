package tree_sitter.c.node

import tree_sitter.Node

public sealed interface EnumeratorNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): EnumeratorNodeChildren {
            val n = createNode(node)
            if (n is EnumeratorNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a EnumeratorNodeChildren")
        }
    }
}

public class EnumeratorNode(
    override val `$node`: Node,
) : CNodeBase,
    EnumeratorListNodeChildren,
    PreprocElifNodeChildren,
    PreprocElifdefNodeChildren,
    PreprocElseNodeChildren,
    PreprocIfNodeChildren,
    PreprocIfdefNodeChildren {
    public val name: IdentifierNode
        get() = IdentifierNode(
            `$node`.getChildByFieldName("name") ?: error("required field name is null")
        )

    public val `value`: ExpressionNode?
        get() = (`$node`.getChildByFieldName("value"))?.let { ExpressionNode(it) }

    public fun children(): List<EnumeratorNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            EnumeratorNodeChildren(it)
        }
    }
}

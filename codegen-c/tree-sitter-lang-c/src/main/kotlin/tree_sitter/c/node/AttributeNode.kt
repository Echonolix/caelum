package tree_sitter.c.node

import tree_sitter.Node

public sealed interface AttributeNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): AttributeNodeChildren {
            val n = createNode(node)
            if (n is AttributeNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a AttributeNodeChildren")
        }
    }
}

public class AttributeNode(
    override val `$node`: Node,
) : CNodeBase,
    AttributeDeclarationNodeChildren {
    public val name: IdentifierNode
        get() = IdentifierNode(
            `$node`.getChildByFieldName("name") ?: error("required field name is null")
        )

    public val prefix: IdentifierNode?
        get() = (`$node`.getChildByFieldName("prefix"))?.let { IdentifierNode(it) }

    public fun children(): AttributeNodeChildren? {
        if (`$node`.namedChildCount == 0U) {
            return null
        }
        return AttributeNodeChildren(`$node`.getNamedChild(0u) ?: error("no child found for attribute"))
    }
}

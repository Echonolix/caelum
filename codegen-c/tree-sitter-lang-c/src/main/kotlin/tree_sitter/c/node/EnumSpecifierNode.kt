package tree_sitter.c.node

import tree_sitter.Node

public sealed interface EnumSpecifierNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): EnumSpecifierNodeChildren {
            val n = createNode(node)
            if (n is EnumSpecifierNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a EnumSpecifierNodeChildren")
        }
    }
}

public class EnumSpecifierNode(
    override val `$node`: Node,
) : CNodeBase,
    TypeSpecifierNode {
    public val body: EnumeratorListNode?
        get() = (`$node`.getChildByFieldName("body"))?.let { EnumeratorListNode(it) }

    public val name: TypeIdentifierNode?
        get() = (`$node`.getChildByFieldName("name"))?.let { TypeIdentifierNode(it) }

    public val underlyingType: PrimitiveTypeNode?
        get() = (`$node`.getChildByFieldName("underlying_type"))?.let { PrimitiveTypeNode(it) }

    public fun children(): EnumSpecifierNodeChildren? {
        if (`$node`.namedChildCount == 0U) {
            return null
        }
        return EnumSpecifierNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for enum_specifier")
        )
    }
}

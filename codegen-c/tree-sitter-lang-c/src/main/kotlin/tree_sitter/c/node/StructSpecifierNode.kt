package tree_sitter.c.node

import tree_sitter.Node

public sealed interface StructSpecifierNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): StructSpecifierNodeChildren {
            val n = createNode(node)
            if (n is StructSpecifierNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a StructSpecifierNodeChildren")
        }
    }
}

public class StructSpecifierNode(
    override val `$node`: Node,
) : CNodeBase,
    TypeSpecifierNode {
    public val body: FieldDeclarationListNode?
        get() = (`$node`.getChildByFieldName("body"))?.let { FieldDeclarationListNode(it) }

    public val name: TypeIdentifierNode?
        get() = (`$node`.getChildByFieldName("name"))?.let { TypeIdentifierNode(it) }

    public fun children(): List<StructSpecifierNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            StructSpecifierNodeChildren(it)
        }
    }
}

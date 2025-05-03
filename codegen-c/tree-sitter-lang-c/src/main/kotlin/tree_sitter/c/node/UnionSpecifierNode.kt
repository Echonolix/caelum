package tree_sitter.c.node

import tree_sitter.Node

public sealed interface UnionSpecifierNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): UnionSpecifierNodeChildren {
            val n = createNode(node)
            if (n is UnionSpecifierNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a UnionSpecifierNodeChildren")
        }
    }
}

public class UnionSpecifierNode(
    override val `$node`: Node,
) : CNodeBase,
    TypeSpecifierNode {
    public val body: FieldDeclarationListNode?
        get() = (`$node`.getChildByFieldName("body"))?.let { FieldDeclarationListNode(it) }

    public val name: TypeIdentifierNode?
        get() = (`$node`.getChildByFieldName("name"))?.let { TypeIdentifierNode(it) }

    public fun children(): List<UnionSpecifierNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            UnionSpecifierNodeChildren(it)
        }
    }
}

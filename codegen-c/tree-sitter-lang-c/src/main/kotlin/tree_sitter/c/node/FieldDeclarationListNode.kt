package tree_sitter.c.node

import tree_sitter.Node

public sealed interface FieldDeclarationListNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): FieldDeclarationListNodeChildren {
            val n = createNode(node)
            if (n is FieldDeclarationListNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a FieldDeclarationListNodeChildren")
        }
    }
}

public class FieldDeclarationListNode(
    override val `$node`: Node,
) : CNodeBase,
    StructSpecifierNodeChildren,
    UnionSpecifierNodeChildren {
    public fun children(): List<FieldDeclarationListNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            FieldDeclarationListNodeChildren(it)
        }
    }
}

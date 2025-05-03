package tree_sitter.c.node

import tree_sitter.Node

public sealed interface DeclarationListNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): DeclarationListNodeChildren {
            val n = createNode(node)
            if (n is DeclarationListNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a DeclarationListNodeChildren")
        }
    }
}

public class DeclarationListNode(
    override val `$node`: Node,
) : CNodeBase,
    LinkageSpecificationNodeChildren,
    LinkageSpecificationNodeBody {
    public fun children(): List<DeclarationListNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            DeclarationListNodeChildren(it)
        }
    }
}

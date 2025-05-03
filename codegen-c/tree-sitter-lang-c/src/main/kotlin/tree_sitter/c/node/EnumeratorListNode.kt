package tree_sitter.c.node

import tree_sitter.Node

public sealed interface EnumeratorListNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): EnumeratorListNodeChildren {
            val n = createNode(node)
            if (n is EnumeratorListNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a EnumeratorListNodeChildren")
        }
    }
}

public class EnumeratorListNode(
    override val `$node`: Node,
) : CNodeBase,
    EnumSpecifierNodeChildren {
    public fun children(): List<EnumeratorListNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            EnumeratorListNodeChildren(it)
        }
    }
}

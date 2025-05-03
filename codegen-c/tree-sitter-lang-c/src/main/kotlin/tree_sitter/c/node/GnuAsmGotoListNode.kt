package tree_sitter.c.node

import tree_sitter.Node

public sealed interface GnuAsmGotoListNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): GnuAsmGotoListNodeChildren {
            val n = createNode(node)
            if (n is GnuAsmGotoListNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a GnuAsmGotoListNodeChildren")
        }
    }
}

public class GnuAsmGotoListNode(
    override val `$node`: Node,
) : CNodeBase,
    GnuAsmExpressionNodeChildren {
    public val label: List<IdentifierNode>
        get() = useCursor { cursor ->
            `$node`.childrenByFieldName("label", cursor)
                .asSequence()
                .map { IdentifierNode(it) }
                .toList()
        }

    public fun children(): List<GnuAsmGotoListNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            GnuAsmGotoListNodeChildren(it)
        }
    }
}

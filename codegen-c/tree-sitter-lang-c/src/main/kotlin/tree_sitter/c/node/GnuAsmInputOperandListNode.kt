package tree_sitter.c.node

import tree_sitter.Node

public sealed interface GnuAsmInputOperandListNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): GnuAsmInputOperandListNodeChildren {
            val n = createNode(node)
            if (n is GnuAsmInputOperandListNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a GnuAsmInputOperandListNodeChildren")
        }
    }
}

public class GnuAsmInputOperandListNode(
    override val `$node`: Node,
) : CNodeBase,
    GnuAsmExpressionNodeChildren {
    public val operand: List<GnuAsmInputOperandNode>
        get() = useCursor { cursor ->
            `$node`.childrenByFieldName("operand", cursor)
                .asSequence()
                .map { GnuAsmInputOperandNode(it) }
                .toList()
        }

    public fun children(): List<GnuAsmInputOperandListNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            GnuAsmInputOperandListNodeChildren(it)
        }
    }
}

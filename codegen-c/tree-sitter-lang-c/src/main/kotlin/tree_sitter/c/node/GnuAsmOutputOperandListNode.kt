package tree_sitter.c.node

import tree_sitter.Node

public sealed interface GnuAsmOutputOperandListNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): GnuAsmOutputOperandListNodeChildren {
            val n = createNode(node)
            if (n is GnuAsmOutputOperandListNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a GnuAsmOutputOperandListNodeChildren")
        }
    }
}

public class GnuAsmOutputOperandListNode(
    override val `$node`: Node,
) : CNodeBase,
    GnuAsmExpressionNodeChildren {
    public val operand: List<GnuAsmOutputOperandNode>
        get() = useCursor { cursor ->
            `$node`.childrenByFieldName("operand", cursor)
                .asSequence()
                .map { GnuAsmOutputOperandNode(it) }
                .toList()
        }

    public fun children(): List<GnuAsmOutputOperandListNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            GnuAsmOutputOperandListNodeChildren(it)
        }
    }
}

package tree_sitter.c.node

import tree_sitter.Node

public sealed interface GnuAsmClobberListNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): GnuAsmClobberListNodeChildren {
            val n = createNode(node)
            if (n is GnuAsmClobberListNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a GnuAsmClobberListNodeChildren")
        }
    }
}

public sealed interface GnuAsmClobberListNodeRegister : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): GnuAsmClobberListNodeRegister {
            val n = createNode(node)
            if (n is GnuAsmClobberListNodeRegister) {
                return n
            }
            throw IllegalArgumentException("Node is not a GnuAsmClobberListNodeRegister")
        }
    }
}

public class GnuAsmClobberListNode(
    override val `$node`: Node,
) : CNodeBase,
    GnuAsmExpressionNodeChildren {
    public val register: List<GnuAsmClobberListNodeRegister>
        get() = useCursor { cursor ->
            `$node`.childrenByFieldName("register", cursor)
                .asSequence()
                .map { GnuAsmClobberListNodeRegister(it) }
                .toList()
        }

    public fun children(): List<GnuAsmClobberListNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            GnuAsmClobberListNodeChildren(it)
        }
    }
}

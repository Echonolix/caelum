package tree_sitter.c.node

import tree_sitter.Node

public sealed interface ConcatenatedStringNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ConcatenatedStringNodeChildren {
            val n = createNode(node)
            if (n is ConcatenatedStringNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a ConcatenatedStringNodeChildren")
        }
    }
}

public class ConcatenatedStringNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode,
    GnuAsmClobberListNodeChildren,
    GnuAsmExpressionNodeChildren,
    GnuAsmClobberListNodeRegister,
    GnuAsmExpressionNodeAssemblyCode {
    public fun children(): List<ConcatenatedStringNodeChildren> = `$node`.namedChildren.map {
        ConcatenatedStringNodeChildren(it)
    }
}

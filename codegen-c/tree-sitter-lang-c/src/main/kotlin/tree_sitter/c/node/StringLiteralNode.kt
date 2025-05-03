package tree_sitter.c.node

import tree_sitter.Node

public sealed interface StringLiteralNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): StringLiteralNodeChildren {
            val n = createNode(node)
            if (n is StringLiteralNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a StringLiteralNodeChildren")
        }
    }
}

public class StringLiteralNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode,
    ConcatenatedStringNodeChildren,
    GnuAsmClobberListNodeChildren,
    GnuAsmExpressionNodeChildren,
    GnuAsmInputOperandNodeChildren,
    GnuAsmOutputOperandNodeChildren,
    LinkageSpecificationNodeChildren,
    PreprocIncludeNodeChildren,
    GnuAsmClobberListNodeRegister,
    GnuAsmExpressionNodeAssemblyCode,
    PreprocIncludeNodePath {
    public fun children(): List<StringLiteralNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            StringLiteralNodeChildren(it)
        }
    }
}

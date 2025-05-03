package tree_sitter.c.node

import tree_sitter.Node

public sealed interface GnuAsmExpressionNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): GnuAsmExpressionNodeChildren {
            val n = createNode(node)
            if (n is GnuAsmExpressionNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a GnuAsmExpressionNodeChildren")
        }
    }
}

public sealed interface GnuAsmExpressionNodeAssemblyCode : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): GnuAsmExpressionNodeAssemblyCode {
            val n = createNode(node)
            if (n is GnuAsmExpressionNodeAssemblyCode) {
                return n
            }
            throw IllegalArgumentException("Node is not a GnuAsmExpressionNodeAssemblyCode")
        }
    }
}

public class GnuAsmExpressionNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode,
    FunctionDeclaratorNodeChildren,
    DeclarationNodeChildren,
    DeclarationNodeDeclarator {
    public val assemblyCode: GnuAsmExpressionNodeAssemblyCode
        get() = GnuAsmExpressionNodeAssemblyCode(
            `$node`.getChildByFieldName("assembly_code") ?: error("required field assembly_code is null")
        )

    public val clobbers: GnuAsmClobberListNode?
        get() = (`$node`.getChildByFieldName("clobbers"))?.let { GnuAsmClobberListNode(it) }

    public val gotoLabels: GnuAsmGotoListNode?
        get() = (`$node`.getChildByFieldName("goto_labels"))?.let { GnuAsmGotoListNode(it) }

    public val inputOperands: GnuAsmInputOperandListNode?
        get() = (`$node`.getChildByFieldName("input_operands"))?.let { GnuAsmInputOperandListNode(it) }

    public val outputOperands: GnuAsmOutputOperandListNode?
        get() = (`$node`.getChildByFieldName("output_operands"))?.let {
            GnuAsmOutputOperandListNode(it)
        }

    public fun children(): List<GnuAsmExpressionNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            GnuAsmExpressionNodeChildren(it)
        }
    }
}

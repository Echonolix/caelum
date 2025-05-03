package tree_sitter.c.node

import tree_sitter.Node

public sealed interface ExpressionNode : CNodeBase, AlignasQualifierNodeChildren,
    ArgumentListNodeChildren, BitfieldClauseNodeChildren, ExpressionStatementNodeChildren,
    ExtensionExpressionNodeChildren, GenericExpressionNodeChildren, InitializerListNodeChildren,
    ParenthesizedExpressionNodeChildren, ReturnStatementNodeChildren,
    SubscriptDesignatorNodeChildren, AbstractArrayDeclaratorNodeChildren,
    ArrayDeclaratorNodeChildren, AssignmentExpressionNodeChildren, BinaryExpressionNodeChildren,
    CallExpressionNodeChildren, CaseStatementNodeChildren, CastExpressionNodeChildren,
    CommaExpressionNodeChildren, ConditionalExpressionNodeChildren, EnumeratorNodeChildren,
    FieldExpressionNodeChildren, ForStatementNodeChildren, GnuAsmInputOperandNodeChildren,
    GnuAsmOutputOperandNodeChildren, InitDeclaratorNodeChildren, InitializerPairNodeChildren,
    PointerExpressionNodeChildren, SizeofExpressionNodeChildren, SubscriptExpressionNodeChildren,
    SubscriptRangeDesignatorNodeChildren, UnaryExpressionNodeChildren, UpdateExpressionNodeChildren,
    AbstractArrayDeclaratorNodeSize, ArrayDeclaratorNodeSize, BinaryExpressionNodeLeft,
    BinaryExpressionNodeRight, CommaExpressionNodeRight, ConditionalExpressionNodeConsequence,
    ForStatementNodeCondition, ForStatementNodeInitializer, ForStatementNodeUpdate,
    InitDeclaratorNodeValue, InitializerPairNodeValue, UnaryExpressionNodeArgument {
    public companion object {
        public operator fun invoke(node: Node): ExpressionNode {
            val n = createNode(node)
            if (n is ExpressionNode) {
                return n
            }
            throw IllegalArgumentException("Node is not a ExpressionNode")
        }
    }
}

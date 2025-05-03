package tree_sitter.c.node

import tree_sitter.Node

public sealed interface CNodeBase {
    public val `$node`: Node

    public class Unnamed(
        override val `$node`: Node,
    ) : CNodeBase,
        AbstractArrayDeclaratorNodeSize,
        ArrayDeclaratorNodeSize,
        AssignmentExpressionNodeOperator,
        BinaryExpressionNodeOperator,
        FieldExpressionNodeOperator,
        PointerExpressionNodeOperator,
        SizedTypeSpecifierNodeSizeSpecifier,
        UnaryExpressionNodeOperator,
        UpdateExpressionNodeOperator
}

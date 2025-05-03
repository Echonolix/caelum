package tree_sitter.c.node

import tree_sitter.Node

public class FieldIdentifierNode(
    override val `$node`: Node,
) : CNodeBase,
    _FieldDeclaratorNode,
    FieldDesignatorNodeChildren,
    FieldExpressionNodeChildren,
    InitializerPairNodeChildren,
    OffsetofExpressionNodeChildren,
    InitializerPairNodeDesignator

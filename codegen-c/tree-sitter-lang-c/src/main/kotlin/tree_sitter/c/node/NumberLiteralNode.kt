package tree_sitter.c.node

import tree_sitter.Node

public class NumberLiteralNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode,
    PreprocElifNodeChildren,
    PreprocIfNodeChildren,
    PreprocElifNodeCondition,
    PreprocIfNodeCondition

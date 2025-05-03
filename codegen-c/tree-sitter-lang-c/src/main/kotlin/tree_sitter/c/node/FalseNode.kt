package tree_sitter.c.node

import tree_sitter.Node

public class FalseNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode

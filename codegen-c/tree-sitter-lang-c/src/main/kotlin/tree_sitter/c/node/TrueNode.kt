package tree_sitter.c.node

import tree_sitter.Node

public class TrueNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode

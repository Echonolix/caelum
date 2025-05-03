package tree_sitter.c.node

import tree_sitter.Node

public class NullNode(
    override val `$node`: Node,
) : CNodeBase,
    ExpressionNode

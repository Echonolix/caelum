package tree_sitter.c.node

import tree_sitter.Node

public class StringContentNode(
    override val `$node`: Node,
) : CNodeBase,
    StringLiteralNodeChildren

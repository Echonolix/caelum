package tree_sitter.c.node

import tree_sitter.Node

public class PreprocArgNode(
    override val `$node`: Node,
) : CNodeBase,
    PreprocCallNodeChildren,
    PreprocDefNodeChildren,
    PreprocFunctionDefNodeChildren

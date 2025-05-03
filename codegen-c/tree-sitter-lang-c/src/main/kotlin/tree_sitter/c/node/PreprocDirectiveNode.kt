package tree_sitter.c.node

import tree_sitter.Node

public class PreprocDirectiveNode(
    override val `$node`: Node,
) : CNodeBase,
    PreprocCallNodeChildren

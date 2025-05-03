package tree_sitter.c.node

import tree_sitter.Node

public class VariadicParameterNode(
    override val `$node`: Node,
) : CNodeBase,
    ParameterListNodeChildren

package tree_sitter.c.node

import tree_sitter.Node

public class SystemLibStringNode(
    override val `$node`: Node,
) : CNodeBase,
    PreprocIncludeNodeChildren,
    PreprocIncludeNodePath

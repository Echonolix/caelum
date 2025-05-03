package tree_sitter.c.node

import tree_sitter.Node

public class GnuAsmQualifierNode(
    override val `$node`: Node,
) : CNodeBase,
    GnuAsmExpressionNodeChildren

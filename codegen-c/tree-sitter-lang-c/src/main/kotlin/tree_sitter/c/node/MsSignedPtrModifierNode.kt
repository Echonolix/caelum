package tree_sitter.c.node

import tree_sitter.Node

public class MsSignedPtrModifierNode(
    override val `$node`: Node,
) : CNodeBase,
    MsPointerModifierNodeChildren

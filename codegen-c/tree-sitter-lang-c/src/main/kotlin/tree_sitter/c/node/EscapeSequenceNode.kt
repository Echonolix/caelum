package tree_sitter.c.node

import tree_sitter.Node

public class EscapeSequenceNode(
    override val `$node`: Node,
) : CNodeBase,
    CharLiteralNodeChildren,
    StringLiteralNodeChildren

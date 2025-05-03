package tree_sitter.c.node

import tree_sitter.Node

public class CharacterNode(
    override val `$node`: Node,
) : CNodeBase,
    CharLiteralNodeChildren

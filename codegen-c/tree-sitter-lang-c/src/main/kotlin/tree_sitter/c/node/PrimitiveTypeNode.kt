package tree_sitter.c.node

import tree_sitter.Node

public class PrimitiveTypeNode(
    override val `$node`: Node,
) : CNodeBase,
    _TypeDeclaratorNode,
    TypeSpecifierNode,
    EnumSpecifierNodeChildren,
    SizedTypeSpecifierNodeChildren,
    SizedTypeSpecifierNodeType

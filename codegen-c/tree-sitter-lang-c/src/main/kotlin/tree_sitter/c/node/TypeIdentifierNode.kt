package tree_sitter.c.node

import tree_sitter.Node

public class TypeIdentifierNode(
    override val `$node`: Node,
) : CNodeBase,
    _TypeDeclaratorNode,
    TypeSpecifierNode,
    EnumSpecifierNodeChildren,
    SizedTypeSpecifierNodeChildren,
    StructSpecifierNodeChildren,
    UnionSpecifierNodeChildren,
    SizedTypeSpecifierNodeType

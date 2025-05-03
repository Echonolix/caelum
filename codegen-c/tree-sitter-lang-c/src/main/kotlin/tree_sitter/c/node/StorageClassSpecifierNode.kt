package tree_sitter.c.node

import tree_sitter.Node

public class StorageClassSpecifierNode(
    override val `$node`: Node,
) : CNodeBase,
    DeclarationNodeChildren,
    FieldDeclarationNodeChildren,
    FunctionDefinitionNodeChildren,
    ParameterDeclarationNodeChildren

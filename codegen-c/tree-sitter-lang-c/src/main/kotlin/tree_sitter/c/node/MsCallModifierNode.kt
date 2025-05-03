package tree_sitter.c.node

import tree_sitter.Node

public class MsCallModifierNode(
    override val `$node`: Node,
) : CNodeBase,
    AbstractParenthesizedDeclaratorNodeChildren,
    FunctionDefinitionNodeChildren,
    ParenthesizedDeclaratorNodeChildren,
    DeclarationNodeChildren,
    DeclarationNodeDeclarator

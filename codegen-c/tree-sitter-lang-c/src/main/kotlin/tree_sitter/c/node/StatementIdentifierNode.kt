package tree_sitter.c.node

import tree_sitter.Node

public class StatementIdentifierNode(
    override val `$node`: Node,
) : CNodeBase,
    GotoStatementNodeChildren,
    LabeledStatementNodeChildren

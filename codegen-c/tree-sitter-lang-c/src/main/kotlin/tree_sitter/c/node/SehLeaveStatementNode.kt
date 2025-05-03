package tree_sitter.c.node

import tree_sitter.Node

public class SehLeaveStatementNode(
    override val `$node`: Node,
) : CNodeBase,
    StatementNode,
    CaseStatementNodeChildren

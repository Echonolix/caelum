package tree_sitter.c.node

import tree_sitter.Node

public sealed interface StatementNode : CNodeBase, AttributedStatementNodeChildren,
    CompoundStatementNodeChildren, DeclarationListNodeChildren, ElseClauseNodeChildren,
    LabeledStatementNodeChildren, PreprocElifNodeChildren, PreprocElifdefNodeChildren,
    PreprocElseNodeChildren, PreprocIfNodeChildren, PreprocIfdefNodeChildren,
    DoStatementNodeChildren, ForStatementNodeChildren, IfStatementNodeChildren,
    WhileStatementNodeChildren {
    public companion object {
        public operator fun invoke(node: Node): StatementNode {
            val n = createNode(node)
            if (n is StatementNode) {
                return n
            }
            throw IllegalArgumentException("Node is not a StatementNode")
        }
    }
}

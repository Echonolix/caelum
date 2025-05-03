package tree_sitter.c.node

import tree_sitter.Node

public sealed interface CompoundStatementNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): CompoundStatementNodeChildren {
            val n = createNode(node)
            if (n is CompoundStatementNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a CompoundStatementNodeChildren")
        }
    }
}

public class CompoundStatementNode(
    override val `$node`: Node,
) : CNodeBase,
    StatementNode,
    ArgumentListNodeChildren,
    CaseStatementNodeChildren,
    ParameterListNodeChildren,
    ParenthesizedExpressionNodeChildren,
    TranslationUnitNodeChildren,
    FunctionDefinitionNodeChildren,
    SehExceptClauseNodeChildren,
    SehFinallyClauseNodeChildren,
    SehTryStatementNodeChildren,
    SwitchStatementNodeChildren {
    public fun children(): List<CompoundStatementNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            CompoundStatementNodeChildren(it)
        }
    }
}

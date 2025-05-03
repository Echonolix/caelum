package tree_sitter.c.node

import tree_sitter.Node

public sealed interface ForStatementNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ForStatementNodeChildren {
            val n = createNode(node)
            if (n is ForStatementNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a ForStatementNodeChildren")
        }
    }
}

public sealed interface ForStatementNodeCondition : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ForStatementNodeCondition {
            val n = createNode(node)
            if (n is ForStatementNodeCondition) {
                return n
            }
            throw IllegalArgumentException("Node is not a ForStatementNodeCondition")
        }
    }
}

public sealed interface ForStatementNodeInitializer : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ForStatementNodeInitializer {
            val n = createNode(node)
            if (n is ForStatementNodeInitializer) {
                return n
            }
            throw IllegalArgumentException("Node is not a ForStatementNodeInitializer")
        }
    }
}

public sealed interface ForStatementNodeUpdate : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): ForStatementNodeUpdate {
            val n = createNode(node)
            if (n is ForStatementNodeUpdate) {
                return n
            }
            throw IllegalArgumentException("Node is not a ForStatementNodeUpdate")
        }
    }
}

public class ForStatementNode(
    override val `$node`: Node,
) : CNodeBase,
    StatementNode,
    CaseStatementNodeChildren,
    TranslationUnitNodeChildren {
    public val body: StatementNode
        get() = StatementNode(
            `$node`.getChildByFieldName("body") ?: error("required field body is null")
        )

    public val condition: ForStatementNodeCondition?
        get() = (`$node`.getChildByFieldName("condition"))?.let { ForStatementNodeCondition(it) }

    public val initializer: ForStatementNodeInitializer?
        get() = (`$node`.getChildByFieldName("initializer"))?.let { ForStatementNodeInitializer(it) }

    public val update: ForStatementNodeUpdate?
        get() = (`$node`.getChildByFieldName("update"))?.let { ForStatementNodeUpdate(it) }

    public fun children(): List<ForStatementNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            ForStatementNodeChildren(it)
        }
    }
}

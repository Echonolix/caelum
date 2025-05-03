package tree_sitter.c.node

import tree_sitter.Node

public sealed interface PreprocIncludeNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocIncludeNodeChildren {
            val n = createNode(node)
            if (n is PreprocIncludeNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocIncludeNodeChildren")
        }
    }
}

public sealed interface PreprocIncludeNodePath : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocIncludeNodePath {
            val n = createNode(node)
            if (n is PreprocIncludeNodePath) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocIncludeNodePath")
        }
    }
}

public class PreprocIncludeNode(
    override val `$node`: Node,
) : CNodeBase,
    CompoundStatementNodeChildren,
    DeclarationListNodeChildren,
    PreprocElifNodeChildren,
    PreprocElifdefNodeChildren,
    PreprocElseNodeChildren,
    PreprocIfNodeChildren,
    PreprocIfdefNodeChildren,
    TranslationUnitNodeChildren {
    public val path: PreprocIncludeNodePath
        get() = PreprocIncludeNodePath(
            `$node`.getChildByFieldName("path") ?: error("required field path is null")
        )

    public fun children(): List<PreprocIncludeNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            PreprocIncludeNodeChildren(it)
        }
    }
}

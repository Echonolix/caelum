package tree_sitter.c.node

import tree_sitter.Node

public sealed interface TranslationUnitNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): TranslationUnitNodeChildren {
            val n = createNode(node)
            if (n is TranslationUnitNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a TranslationUnitNodeChildren")
        }
    }
}

public class TranslationUnitNode(
    override val `$node`: Node,
) : CNodeBase {
    public fun children(): List<TranslationUnitNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            TranslationUnitNodeChildren(it)
        }
    }
}

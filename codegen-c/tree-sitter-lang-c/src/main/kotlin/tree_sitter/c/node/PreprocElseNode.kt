package tree_sitter.c.node

import tree_sitter.Node

public sealed interface PreprocElseNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): PreprocElseNodeChildren {
            val n = createNode(node)
            if (n is PreprocElseNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a PreprocElseNodeChildren")
        }
    }
}

public class PreprocElseNode(
    override val `$node`: Node,
) : CNodeBase,
    PreprocElifNodeChildren,
    PreprocElifdefNodeChildren,
    PreprocIfNodeChildren,
    PreprocIfdefNodeChildren,
    PreprocElifNodeAlternative,
    PreprocElifdefNodeAlternative,
    PreprocIfNodeAlternative,
    PreprocIfdefNodeAlternative {
    public fun children(): List<PreprocElseNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            PreprocElseNodeChildren(it)
        }
    }
}

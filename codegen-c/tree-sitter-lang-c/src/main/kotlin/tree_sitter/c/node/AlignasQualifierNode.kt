package tree_sitter.c.node

import tree_sitter.Node

public sealed interface AlignasQualifierNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): AlignasQualifierNodeChildren {
            val n = createNode(node)
            if (n is AlignasQualifierNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a AlignasQualifierNodeChildren")
        }
    }
}

public class AlignasQualifierNode(
    override val `$node`: Node,
) : CNodeBase,
    TypeQualifierNodeChildren {
    public fun children(): AlignasQualifierNodeChildren =
        AlignasQualifierNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for alignas_qualifier")
        )
}

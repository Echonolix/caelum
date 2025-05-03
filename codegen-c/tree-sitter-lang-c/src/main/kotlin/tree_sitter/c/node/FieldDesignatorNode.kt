package tree_sitter.c.node

import tree_sitter.Node

public sealed interface FieldDesignatorNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): FieldDesignatorNodeChildren {
            val n = createNode(node)
            if (n is FieldDesignatorNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a FieldDesignatorNodeChildren")
        }
    }
}

public class FieldDesignatorNode(
    override val `$node`: Node,
) : CNodeBase,
    InitializerPairNodeChildren,
    InitializerPairNodeDesignator {
    public fun children(): FieldDesignatorNodeChildren =
        FieldDesignatorNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for field_designator")
        )
}

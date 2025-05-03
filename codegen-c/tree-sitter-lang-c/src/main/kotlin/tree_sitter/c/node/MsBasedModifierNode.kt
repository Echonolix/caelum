package tree_sitter.c.node

import tree_sitter.Node

public sealed interface MsBasedModifierNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): MsBasedModifierNodeChildren {
            val n = createNode(node)
            if (n is MsBasedModifierNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a MsBasedModifierNodeChildren")
        }
    }
}

public class MsBasedModifierNode(
    override val `$node`: Node,
) : CNodeBase,
    PointerDeclaratorNodeChildren {
    public fun children(): MsBasedModifierNodeChildren =
        MsBasedModifierNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for ms_based_modifier")
        )
}

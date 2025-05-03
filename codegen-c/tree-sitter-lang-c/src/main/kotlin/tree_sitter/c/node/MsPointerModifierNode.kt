package tree_sitter.c.node

import tree_sitter.Node

public sealed interface MsPointerModifierNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): MsPointerModifierNodeChildren {
            val n = createNode(node)
            if (n is MsPointerModifierNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a MsPointerModifierNodeChildren")
        }
    }
}

public class MsPointerModifierNode(
    override val `$node`: Node,
) : CNodeBase,
    AbstractPointerDeclaratorNodeChildren,
    PointerDeclaratorNodeChildren {
    public fun children(): MsPointerModifierNodeChildren =
        MsPointerModifierNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for ms_pointer_modifier")
        )
}

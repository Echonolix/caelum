package tree_sitter.c.node

import tree_sitter.Node

public sealed interface MacroTypeSpecifierNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): MacroTypeSpecifierNodeChildren {
            val n = createNode(node)
            if (n is MacroTypeSpecifierNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a MacroTypeSpecifierNodeChildren")
        }
    }
}

public class MacroTypeSpecifierNode(
    override val `$node`: Node,
) : CNodeBase,
    TypeSpecifierNode {
    public val name: IdentifierNode
        get() = IdentifierNode(
            `$node`.getChildByFieldName("name") ?: error("required field name is null")
        )

    public val type: TypeDescriptorNode
        get() = TypeDescriptorNode(
            `$node`.getChildByFieldName("type") ?: error("required field type is null")
        )

    public fun children(): List<MacroTypeSpecifierNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            MacroTypeSpecifierNodeChildren(it)
        }
    }
}

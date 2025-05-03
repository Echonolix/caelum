package tree_sitter.c.node

import tree_sitter.Node

public sealed interface MsDeclspecModifierNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): MsDeclspecModifierNodeChildren {
            val n = createNode(node)
            if (n is MsDeclspecModifierNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a MsDeclspecModifierNodeChildren")
        }
    }
}

public class MsDeclspecModifierNode(
    override val `$node`: Node,
) : CNodeBase,
    DeclarationNodeChildren,
    FieldDeclarationNodeChildren,
    FunctionDefinitionNodeChildren,
    ParameterDeclarationNodeChildren,
    StructSpecifierNodeChildren,
    UnionSpecifierNodeChildren {
    public fun children(): MsDeclspecModifierNodeChildren =
        MsDeclspecModifierNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for ms_declspec_modifier")
        )
}

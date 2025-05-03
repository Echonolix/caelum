package tree_sitter.c.node

import tree_sitter.Node

public sealed interface AttributeSpecifierNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): AttributeSpecifierNodeChildren {
            val n = createNode(node)
            if (n is AttributeSpecifierNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a AttributeSpecifierNodeChildren")
        }
    }
}

public class AttributeSpecifierNode(
    override val `$node`: Node,
) : CNodeBase,
    DeclarationNodeChildren,
    EnumSpecifierNodeChildren,
    FieldDeclarationNodeChildren,
    FunctionDeclaratorNodeChildren,
    FunctionDefinitionNodeChildren,
    ParameterDeclarationNodeChildren,
    StructSpecifierNodeChildren,
    TypeDefinitionNodeChildren,
    UnionSpecifierNodeChildren {
    public fun children(): AttributeSpecifierNodeChildren =
        AttributeSpecifierNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for attribute_specifier")
        )
}

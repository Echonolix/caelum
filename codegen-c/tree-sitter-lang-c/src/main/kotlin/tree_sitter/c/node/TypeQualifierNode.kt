package tree_sitter.c.node

import tree_sitter.Node

public sealed interface TypeQualifierNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): TypeQualifierNodeChildren {
            val n = createNode(node)
            if (n is TypeQualifierNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a TypeQualifierNodeChildren")
        }
    }
}

public class TypeQualifierNode(
    override val `$node`: Node,
) : CNodeBase,
    AbstractArrayDeclaratorNodeChildren,
    AbstractPointerDeclaratorNodeChildren,
    ArrayDeclaratorNodeChildren,
    DeclarationNodeChildren,
    FieldDeclarationNodeChildren,
    FunctionDefinitionNodeChildren,
    ParameterDeclarationNodeChildren,
    PointerDeclaratorNodeChildren,
    SizedTypeSpecifierNodeChildren,
    TypeDefinitionNodeChildren,
    TypeDescriptorNodeChildren {
    public fun children(): TypeQualifierNodeChildren? {
        if (`$node`.namedChildCount == 0U) {
            return null
        }
        return TypeQualifierNodeChildren(
            `$node`.getNamedChild(0u) ?: error("no child found for type_qualifier")
        )
    }
}

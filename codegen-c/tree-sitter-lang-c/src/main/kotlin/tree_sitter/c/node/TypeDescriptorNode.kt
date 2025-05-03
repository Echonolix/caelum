package tree_sitter.c.node

import tree_sitter.Node

public sealed interface TypeDescriptorNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): TypeDescriptorNodeChildren {
            val n = createNode(node)
            if (n is TypeDescriptorNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a TypeDescriptorNodeChildren")
        }
    }
}

public class TypeDescriptorNode(
    override val `$node`: Node,
) : CNodeBase,
    AlignasQualifierNodeChildren,
    GenericExpressionNodeChildren,
    AlignofExpressionNodeChildren,
    CastExpressionNodeChildren,
    CompoundLiteralExpressionNodeChildren,
    MacroTypeSpecifierNodeChildren,
    OffsetofExpressionNodeChildren,
    SizeofExpressionNodeChildren {
    public val declarator: _AbstractDeclaratorNode?
        get() = (`$node`.getChildByFieldName("declarator"))?.let { _AbstractDeclaratorNode(it) }

    public val type: TypeSpecifierNode
        get() = TypeSpecifierNode(
            `$node`.getChildByFieldName("type") ?: error("required field type is null")
        )

    public fun children(): List<TypeDescriptorNodeChildren> {
        if (`$node`.namedChildCount == 0U) {
            return emptyList()
        }
        return `$node`.namedChildren.map {
            TypeDescriptorNodeChildren(it)
        }
    }
}

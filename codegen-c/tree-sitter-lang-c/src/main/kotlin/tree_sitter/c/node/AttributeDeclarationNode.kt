package tree_sitter.c.node

import tree_sitter.Node

public sealed interface AttributeDeclarationNodeChildren : CNodeBase {
    public companion object {
        public operator fun invoke(node: Node): AttributeDeclarationNodeChildren {
            val n = createNode(node)
            if (n is AttributeDeclarationNodeChildren) {
                return n
            }
            throw IllegalArgumentException("Node is not a AttributeDeclarationNodeChildren")
        }
    }
}

public class AttributeDeclarationNode(
    override val `$node`: Node,
) : CNodeBase,
    AttributedDeclaratorNodeChildren,
    AttributedStatementNodeChildren,
    DeclarationNodeChildren,
    FieldDeclarationNodeChildren,
    FunctionDefinitionNodeChildren,
    ParameterDeclarationNodeChildren {
    public fun children(): List<AttributeDeclarationNodeChildren> = `$node`.namedChildren.map {
        AttributeDeclarationNodeChildren(it)
    }
}

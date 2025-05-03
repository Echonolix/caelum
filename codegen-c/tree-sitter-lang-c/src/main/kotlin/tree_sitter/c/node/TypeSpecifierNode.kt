package tree_sitter.c.node

import tree_sitter.Node

public sealed interface TypeSpecifierNode : CNodeBase, CompoundStatementNodeChildren,
    DeclarationListNodeChildren, PreprocElifNodeChildren, PreprocElifdefNodeChildren,
    PreprocElseNodeChildren, PreprocIfNodeChildren, PreprocIfdefNodeChildren,
    TranslationUnitNodeChildren, DeclarationNodeChildren, FieldDeclarationNodeChildren,
    FunctionDefinitionNodeChildren, ParameterDeclarationNodeChildren, TypeDefinitionNodeChildren,
    TypeDescriptorNodeChildren {
    public companion object {
        public operator fun invoke(node: Node): TypeSpecifierNode {
            val n = createNode(node)
            if (n is TypeSpecifierNode) {
                return n
            }
            throw IllegalArgumentException("Node is not a TypeSpecifierNode")
        }
    }
}

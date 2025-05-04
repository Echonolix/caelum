package c.ast.visitor

import c.ast.ParseContext
import c.lang.CPrimitiveType
import c.lang.CSizeSpecifier
import tree_sitter.Range
import tree_sitter.c.node.ExpressionNode
import tree_sitter.c.node.TypeDefinitionNode

interface ASTVisitor {
    /**
     * See [Line Marker](https://gcc.gnu.org/onlinedocs/gcc-3.0.2/cpp_9.html)
     */
    fun visitLineMarker(
        lineNum: Int,
        fileName: String,
        newFile: Boolean,
        returnFile: Boolean,
        fromSysHeader: Boolean,
        pos: Range
    )

    fun visitComment(comment: String)

    fun visitTypedef(ast: TypeDefinitionNode): TypeDefVisitor

    fun visitStructSpecifier(): GroupSpecifierVisitor
    fun visitUnionSpecifier(): GroupSpecifierVisitor

    fun visitEnumSpecifier(): EnumVisitor

    fun visitDeclaration() : DeclarationVisitor
}

interface DeclarationVisitor {
    fun visitType(): TypeSpecifierVisitor
    fun visitDeclarator(): DeclaratorVisitor
    fun visitEnd()
}


interface TypeSpecifierVisitor {
    fun visitPrimitiveType(type: CPrimitiveType)
    fun visitSizedTypeSpecifier(): SizedTypeSpecifierVisitor
    fun visitTypeIdentifier(name: String)

    fun visitStructSpecifier(): GroupSpecifierVisitor
    fun visitUnionSpecifier(): GroupSpecifierVisitor

    fun visitEnumSpecifier(): EnumVisitor


    fun visitEnd()
}

interface EnumVisitor {
    fun visitName(name: String)
    fun visitComment(comment: String)
    fun visitEnumerator(name: String, value: ExpressionNode?, ctx: ParseContext)
    fun visitEnd()
}

interface GroupSpecifierVisitor {
    fun visitName(name: String)

    fun visitField(): FieldDeclarationVisitor
    fun visitComment(comment: String)

    fun visitEnd()
}


interface FieldDeclarationVisitor {
    fun visitType(): TypeSpecifierVisitor
    fun visitDeclarator(): DeclaratorVisitor
    fun visitEnd()
}

interface SizedTypeSpecifierVisitor {
    fun visitSizedSpecifier(specifier: CSizeSpecifier)
    fun visitType(type: CPrimitiveType)
    fun visitEnd()
}

interface TypeDefVisitor {
    fun visitType(): TypeSpecifierVisitor
    fun visitDeclarator(): DeclaratorVisitor
    fun visitEnd()
}

interface DeclaratorVisitor {


    fun visitFunction(): FunctionVisitor

    fun visitIdentifier(name: String)
    fun visitFieldIdentifier(name: String)

    fun visitArray(size: String?)

    fun visitPointer()

    fun visitEnd()

}

interface FunctionVisitor {
    fun visitParameter(): ParameterVisitor
    fun visitEnd()
}

interface ParameterVisitor {
    fun visitType(): TypeSpecifierVisitor
    fun visitDeclarator(): DeclaratorVisitor
    fun visitEnd()
}
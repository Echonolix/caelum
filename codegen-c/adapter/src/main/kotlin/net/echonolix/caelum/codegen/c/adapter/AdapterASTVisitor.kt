package net.echonolix.caelum

import c.ast.visitor.*
import c.ast.visitor.ASTVisitor
import net.echonolix.caelum.codegen.c.adapter.BuildDeclarationVisitor
import net.echonolix.caelum.codegen.c.adapter.BuildEnumVisitor
import net.echonolix.caelum.codegen.c.adapter.BuildTypedefVisitor
import net.echonolix.caelum.codegen.c.adapter.CStruct
import net.echonolix.caelum.codegen.c.adapter.CommonGroupSpecifierVisitor
import net.echonolix.caelum.codegen.c.adapter.ElementContext
import net.echonolix.caelum.codegen.c.adapter.LineMarker
import tree_sitter.Range
import tree_sitter.c.node.TypeDefinitionNode

class AdapterASTVisitor(val ctx: ElementContext) : ASTVisitor {
    lateinit var lineMarker: LineMarker

    override fun visitLineMarker(
        lineNum: Int,
        fileName: String,
        newFile: Boolean,
        returnFile: Boolean,
        fromSysHeader: Boolean,
        pos: Range
    ) {
        lineMarker = LineMarker(lineNum, fileName, newFile, returnFile, fromSysHeader, pos)
    }

    override fun visitComment(comment: String) {

//        println(ctx.lineMarker.lineNum)
//        println(ctx.lineMarker.fileName)
//
//        println(comment)
//
//        TODO()
    }

    override fun visitTypedef(ast: TypeDefinitionNode): TypeDefVisitor {
        println(lineMarker.fileName)
        println(lineMarker.posOf(ast))
        return BuildTypedefVisitor()
    }

    override fun visitStructSpecifier(): GroupSpecifierVisitor {
        val visitor = CommonGroupSpecifierVisitor()
        return object : GroupSpecifierVisitor by visitor {
            override fun visitEnd() {
                println(
                    CStruct(
                        id = visitor.identifier,
                        fields = visitor.fields,
                    )
                )
            }
        }
    }

    override fun visitUnionSpecifier(): GroupSpecifierVisitor {
        TODO("Not yet implemented")
    }

    override fun visitEnumSpecifier(): EnumVisitor {
        val visitor = BuildEnumVisitor()
        return object : EnumVisitor by visitor {
            override fun visitEnd() {
                println(visitor.build())
            }
        }
    }

    override fun visitDeclaration(): DeclarationVisitor {
        val visitor = BuildDeclarationVisitor()
        return object : DeclarationVisitor by visitor {

            override fun visitEnd() {
                println("DECL: ${visitor.identifier} ${visitor.cType} ")
            }
        }
    }
}


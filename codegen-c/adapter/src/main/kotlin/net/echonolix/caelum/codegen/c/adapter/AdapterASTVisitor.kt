package net.echonolix.caelum.codegen.c.adapter

import c.ast.visitor.*
import tree_sitter.Range
import tree_sitter.c.node.TypeDefinitionNode
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

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
//        println("Comment: $comment")
    }

    override fun visitTypedef(ast: TypeDefinitionNode): TypeDefVisitor {
//        println(Path(lineMarker.fileName).absolutePathString())
//        println(lineMarker.posOf(ast))
        val visitor = BaseTypedefVisitor()
        return object : TypeDefVisitor by visitor {
            override fun visitEnd() {
                if (lineMarker.filePath().absolutePathString() in ctx.inputPathStrs) {
                    val identifier = visitor.identifier!!
                    ctx.addTypedef(identifier.name, visitor.cType)
                }
            }
        }
    }

    override fun visitStructSpecifier(): GroupSpecifierVisitor {
        val visitor = BaseGroupSpecifierVisitor()
        return object : GroupSpecifierVisitor by visitor {
            override fun visitEnd() {
                val identifier = visitor.identifier!!
                ctx.addStruct(identifier.name, CStruct(identifier, visitor.fields))
            }
        }
    }

    override fun visitUnionSpecifier(): GroupSpecifierVisitor {
        val visitor = BaseGroupSpecifierVisitor()
        return object : GroupSpecifierVisitor by visitor {
            override fun visitEnd() {
                val identifier = visitor.identifier!!
                ctx.addUnion(identifier.name, CUnion(identifier, visitor.fields))
            }
        }
    }

    override fun visitEnumSpecifier(): EnumVisitor {
        val visitor = BuildEnumVisitor()
        return object : EnumVisitor by visitor {
            override fun visitEnd() {
                val cEnum = visitor.build()
                val identifier = cEnum.id!!
                ctx.addEnum(identifier.name, cEnum)
            }
        }
    }

    override fun visitDeclaration(): DeclarationVisitor {
        val visitor = BuildDeclarationVisitor()
        return object : DeclarationVisitor by visitor {
            override fun visitEnd() {
                val identifier = visitor.identifier!!
                val cType = visitor.cType
                when (cType) {
                    is CFunction -> ctx.addFunction(identifier.name, cType)
                    else -> throw UnsupportedOperationException("Unsupported type: $identifier $cType")
                }
            }
        }
    }
}


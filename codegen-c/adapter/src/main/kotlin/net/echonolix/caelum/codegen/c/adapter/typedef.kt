package net.echonolix.caelum.codegen.c.adapter

import c.ast.visitor.DeclaratorVisitor
import c.ast.visitor.FunctionVisitor
import c.ast.visitor.TypeDefVisitor
import c.ast.visitor.TypeSpecifierVisitor


class BuildTypedefVisitor() : TypeDefVisitor {

    lateinit var cType: CType
    var identifier: Identifier? = null

    override fun visitType(): TypeSpecifierVisitor {
        val visitor = CTypeSpecifierVisitor()
        return object : TypeSpecifierVisitor by visitor {
            override fun visitEnd() {
                visitor.visitEnd()
                cType = visitor.cType
            }
        }
    }

    override fun visitDeclarator(): DeclaratorVisitor {
        val visitor = CommonDeclaratorVisitor(cType)
        return object : DeclaratorVisitor by visitor {

            override fun visitIdentifier(name: String) {
                identifier = Identifier(name)
            }

            override fun visitEnd() {
                cType = visitor.type
            }
        }
    }

    override fun visitEnd() {
        println("TYPEDEF")
        println(identifier!!)
        println(cType)
        println()
    }
}

class CommonDeclaratorVisitor() : DeclaratorVisitor {

    lateinit var type: CType

    constructor(baseType: CType) : this() {
        type = baseType
    }

    override fun visitFunction(): FunctionVisitor {
        val visitor = BuildFunctionVisitor(type)
        return object : FunctionVisitor by visitor {
            override fun visitEnd() {
                type = visitor.cFunction
            }
        }
    }

    override fun visitIdentifier(name: String) {
        TODO()
    }

    override fun visitFieldIdentifier(name: String) {
        TODO("Not yet implemented")
    }

    override fun visitArray() {
        type = CArrayType(type, -1)
    }

    override fun visitPointer() {
        type = CPointer(type)
    }

    override fun visitEnd() {
//        println(type)
    }
}
package net.echonolix.caelum

import c.ast.visitor.DeclaratorVisitor
import c.ast.visitor.FunctionVisitor
import c.ast.visitor.TypeDefVisitor
import c.ast.visitor.TypeSpecifierVisitor


class BuildTypedefVisitor() : TypeDefVisitor {
    lateinit var cType: CType
    lateinit var identifier: String

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
                identifier = name
            }

            override fun visitEnd() {
                cType = visitor.cType
            }
        }
    }

    override fun visitEnd() {}
}

class CommonDeclaratorVisitor() : DeclaratorVisitor {
    lateinit var cType: CType

    constructor(baseType: CType) : this() {
        cType = baseType
    }

    override fun visitFunction(): FunctionVisitor {
        val visitor = BuildFunctionVisitor(cType)
        return object : FunctionVisitor by visitor {
            override fun visitEnd() {
                visitor.visitEnd()
                cType = visitor.cFunction
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
//        type = CArrayType(type, -1)
    }

    override fun visitPointer() {
//        type = CPointer(type)
    }

    override fun visitEnd() {
//        println(type)
    }
}
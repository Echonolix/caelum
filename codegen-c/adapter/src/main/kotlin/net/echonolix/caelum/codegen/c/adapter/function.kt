package net.echonolix.caelum.codegen.c.adapter

import c.ast.visitor.DeclaratorVisitor
import c.ast.visitor.FunctionVisitor
import c.ast.visitor.ParameterVisitor
import c.ast.visitor.TypeSpecifierVisitor
import kotlin.collections.plus

class BuildFunctionVisitor(returnType: CType) : FunctionVisitor {

    var cFunction = CFunction(emptyList(), returnType)

    override fun visitParameter(): ParameterVisitor {
        val visitor = BuildParameterVisitor()
        return object : ParameterVisitor by visitor {
            override fun visitEnd() {
                cFunction = cFunction.copy(params = cFunction.params + CFunctionParam(visitor.identifier, visitor.type))
            }
        }
    }

    override fun visitEnd() {
        println(cFunction)
    }

}


class BuildParameterVisitor() : ParameterVisitor {
    lateinit var type: CType
    var identifier: Identifier? = null


    override fun visitType(): TypeSpecifierVisitor {
        val visitor = CTypeSpecifierVisitor()
        return object : TypeSpecifierVisitor by visitor {
            override fun visitEnd() {
                type = visitor.cType
            }
        }
    }

    override fun visitDeclarator(): DeclaratorVisitor {
        val visitor = ParameterDeclaratorVisitor(build = this)
        return object : DeclaratorVisitor by visitor {
            override fun visitEnd() {
                identifier = visitor.identifier
                type = visitor.common.type
            }
        }
    }

    override fun visitEnd() {

    }

}

class ParameterDeclaratorVisitor(
    val common: CommonDeclaratorVisitor = CommonDeclaratorVisitor(),
    private val build: BuildParameterVisitor
) : DeclaratorVisitor by common {

    var identifier: Identifier? = null

    init {
        common.type = build.type
    }

    override fun visitIdentifier(name: String) {
        identifier = Identifier(name)
    }


}
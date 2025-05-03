package net.echonolix.caelum

import c.ast.visitor.DeclaratorVisitor
import c.ast.visitor.FunctionVisitor
import c.ast.visitor.ParameterVisitor
import c.ast.visitor.TypeSpecifierVisitor


class BuildFunctionVisitor(val returnType: CType) : FunctionVisitor {
    lateinit var cFunction: CType.Function
    val params = mutableListOf<CType.Function.Parameter>()

    override fun visitParameter(): ParameterVisitor {
        val visitor = BuildParameterVisitor()
        return object : ParameterVisitor by visitor {
            override fun visitEnd() {
                visitor.visitEnd()
                params.add(visitor.parameter)
            }
        }
    }

    override fun visitEnd() {
        cFunction = CType.Function("UNNAMED", returnType, params)
    }
}


class BuildParameterVisitor() : ParameterVisitor {
    lateinit var type: CType
    lateinit var identifier: String
    lateinit var parameter: CType.Function.Parameter

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
                type = visitor.common.cType
            }
        }
    }

    override fun visitEnd() {
        parameter = CType.Function.Parameter(identifier, type)
    }
}

class ParameterDeclaratorVisitor(
    val common: CommonDeclaratorVisitor = CommonDeclaratorVisitor(),
    private val build: BuildParameterVisitor
) : DeclaratorVisitor by common {

    lateinit var identifier: String

    init {
        common.cType = build.type
    }

    override fun visitIdentifier(name: String) {
        this.identifier = name
    }
}
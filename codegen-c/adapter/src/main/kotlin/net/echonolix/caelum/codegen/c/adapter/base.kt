package net.echonolix.caelum.codegen.c.adapter

import c.ast.visitor.*
import c.lang.CPrimitiveType
import c.lang.CSizeSpecifier

class CSizedTypeSpecifierVisitor : SizedTypeSpecifierVisitor {
    var cType: CBasicType = CBasicType(emptyList(), null)

    override fun visitSizedSpecifier(specifier: CSizeSpecifier) {
        cType = cType.copy(sizeSpecifiers = cType.sizeSpecifiers + specifier)
    }

    override fun visitType(type: CPrimitiveType) {
        cType = cType.copy(basic = type)
    }

    override fun visitEnd() {

    }
}

class CTypeSpecifierVisitor : TypeSpecifierVisitor {
    lateinit var cType: CType

    override fun visitPrimitiveType(type: CPrimitiveType) {
        cType = CBasicType(emptyList(), type)
    }

    override fun visitSizedTypeSpecifier(): SizedTypeSpecifierVisitor {
        val visitor = CSizedTypeSpecifierVisitor()
        return object : SizedTypeSpecifierVisitor by visitor {
            override fun visitEnd() {
                cType = visitor.cType
            }
        }
    }

    override fun visitTypeIdentifier(name: String) {
        cType = Identifier(name)
    }

    override fun visitStructSpecifier(): GroupSpecifierVisitor {
        val visitor = BaseGroupSpecifierVisitor()
        return object : GroupSpecifierVisitor by visitor {
            override fun visitEnd() {
                cType = CStruct(
                    id = visitor.identifier,
                    fields = visitor.fields,
                )
            }
        }
    }

    override fun visitUnionSpecifier(): GroupSpecifierVisitor {
        val visitor = BaseGroupSpecifierVisitor()
        return object : GroupSpecifierVisitor by visitor {
            override fun visitEnd() {
                cType = CStruct(
                    id = visitor.identifier,
                    fields = visitor.fields,
                )
            }
        }
    }

    override fun visitEnumSpecifier(): EnumVisitor {
        val visitor = BuildEnumVisitor()
        return object : EnumVisitor by visitor {
            override fun visitEnd() {
                cType = visitor.build()
            }
        }
    }

    override fun visitEnd() {
        if (!this::cType.isInitialized) {
            cType = CUnresolved
        }
    }
}

class BuildDeclarationVisitor() : DeclarationVisitor {
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
        val visitor = BaseDeclaratorVisitor(cType)
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

    }

}

class BaseTypedefVisitor() : TypeDefVisitor {
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
        val visitor = BaseDeclaratorVisitor(cType)
        return object : DeclaratorVisitor by visitor {

            override fun visitIdentifier(name: String) {
                identifier = Identifier(name)
            }

            override fun visitEnd() {
                cType = visitor.type
            }
        }
    }

    override fun visitEnd() {}
}

class BaseDeclaratorVisitor() : DeclaratorVisitor {
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

    override fun visitEnd() {}
}
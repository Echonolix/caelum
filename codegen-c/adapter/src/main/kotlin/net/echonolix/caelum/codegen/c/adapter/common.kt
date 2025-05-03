package net.echonolix.caelum.codegen.c.adapter

import c.ast.visitor.*
import c.lang.CPrimitiveType
import c.lang.CSizeSpecifier
import kotlin.collections.plusAssign

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
        val visitor = CommonGroupSpecifierVisitor()
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
        val visitor = CommonGroupSpecifierVisitor()
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


class CommonGroupSpecifierVisitor : GroupSpecifierVisitor {

    var identifier: Identifier? = null

    val fields = mutableListOf<CField>()

    override fun visitName(name: String) {
        identifier = Identifier(name)
    }

    override fun visitField(): FieldDeclarationVisitor {
        println("New field for $identifier")
        return object : FieldDeclarationVisitor {

            lateinit var cType: CType
            var identifier: Identifier? = null

            override fun visitType(): TypeSpecifierVisitor {
                val common = CTypeSpecifierVisitor()
                return object : TypeSpecifierVisitor by common {
                    override fun visitEnd() {
                        cType = common.cType
                    }
                }
            }

            override fun visitDeclarator(): DeclaratorVisitor {
                val common = CommonDeclaratorVisitor(cType)
                return object : DeclaratorVisitor by common {

                    override fun visitFieldIdentifier(name: String) {
                        identifier = Identifier(name)
                    }

                    override fun visitEnd() {
                        cType = common.type
                    }
                }
            }

            override fun visitEnd() {
                fields += CField(identifier!!, cType)
            }

        }
    }

    override fun visitComment(comment: String) {

    }

    override fun visitEnd() {

    }

}


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

    }

}
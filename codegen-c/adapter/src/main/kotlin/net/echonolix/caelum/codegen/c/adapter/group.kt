package net.echonolix.caelum.codegen.c.adapter

import c.ast.visitor.DeclaratorVisitor
import c.ast.visitor.FieldDeclarationVisitor
import c.ast.visitor.GroupSpecifierVisitor
import c.ast.visitor.TypeSpecifierVisitor

class BaseGroupSpecifierVisitor : GroupSpecifierVisitor {
    var identifier: Identifier? = null
    val fields = mutableListOf<CField>()

    override fun visitName(name: String) {
        identifier = Identifier(name)
    }

    override fun visitField(): FieldDeclarationVisitor {
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
                val common = BaseDeclaratorVisitor(cType)
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

    override fun visitComment(comment: String) {}

    override fun visitEnd() {}
}
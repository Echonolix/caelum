package net.echonolix.caelum

import c.ast.visitor.*
import c.lang.CPrimitiveType
import c.lang.CSizeSpecifier

private fun CPrimitiveType.toBasicType(): CBasicType<*> {
    return when (this) {
        CPrimitiveType.Void -> CBasicType.void
        CPrimitiveType.Char -> CBasicType.char
        CPrimitiveType.Bool -> throw UnsupportedOperationException("Bool is not supported")
        CPrimitiveType.Int -> CBasicType.int
        CPrimitiveType.Float -> CBasicType.float
        CPrimitiveType.Double -> CBasicType.double
    }
}

private class CSizedTypeSpecifierVisitor : SizedTypeSpecifierVisitor {
    lateinit var cBasicType: CBasicType<*>
    lateinit var cType: CType.BasicType

    override fun visitSizedSpecifier(specifier: CSizeSpecifier) {
        cBasicType = when(specifier) {
            CSizeSpecifier.Signed -> cBasicType.toSigned()
            CSizeSpecifier.Unsigned -> cBasicType.toUnsigned()
            CSizeSpecifier.Short -> cBasicType.toShort()
            CSizeSpecifier.Long -> cBasicType.toLong()
        }
    }

    override fun visitType(type: CPrimitiveType) {
        cBasicType = type.toBasicType()
    }

    override fun visitEnd() {
        cType = cBasicType.cType
    }
}

class CTypeSpecifierVisitor : TypeSpecifierVisitor {
    lateinit var cType: CType

    override fun visitPrimitiveType(type: CPrimitiveType) {
        cType = type.toBasicType().cType
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
        println(name)
//        cType = Identifier(name)
    }

    override fun visitStructSpecifier(): GroupSpecifierVisitor {
        val visitor = CommonGroupSpecifierVisitor()
        return object : GroupSpecifierVisitor by visitor {
            override fun visitEnd() {
                cType = CType.Struct(
                    visitor.identifier,
                    visitor.members,
                )
            }
        }
    }

    override fun visitUnionSpecifier(): GroupSpecifierVisitor {
        val visitor = CommonGroupSpecifierVisitor()
        return object : GroupSpecifierVisitor by visitor {
            override fun visitEnd() {
                cType = CType.Union(
                    visitor.identifier,
                    visitor.members,
                )
            }
        }
    }

    override fun visitEnumSpecifier(): EnumVisitor {
        val visitor = BuildEnumVisitor()
        return object : EnumVisitor by visitor {
            override fun visitEnd() {
//                cType = visitor.build()
            }
        }
    }

    override fun visitEnd() {
//        if (!this::cType.isInitialized) {
//            cType = CUnresolved
//        }
    }
}


class CommonGroupSpecifierVisitor : GroupSpecifierVisitor {
    lateinit var identifier: String
    val members = mutableListOf<CType.Group.Member>()

    override fun visitName(name: String) {
        this.identifier = name
    }

    override fun visitField(): FieldDeclarationVisitor {
        println("New field for $identifier")
        return object : FieldDeclarationVisitor {
            lateinit var cType: CType
            lateinit var identifier: String

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
                        identifier = name
                    }

                    override fun visitEnd() {
                        cType = common.cType
                    }
                }
            }

            override fun visitEnd() {
                members += CType.Group.Member(identifier, cType)
            }
        }
    }

    override fun visitComment(comment: String) {}
    
    override fun visitEnd() {}
}

class BuildDeclarationVisitor() : DeclarationVisitor {
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
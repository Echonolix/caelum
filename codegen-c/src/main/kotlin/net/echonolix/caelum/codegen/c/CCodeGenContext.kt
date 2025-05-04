package net.echonolix.caelum.codegen.c

import c.lang.CPrimitiveType
import c.lang.CSizeSpecifier
import net.echonolix.caelum.CBasicType
import net.echonolix.caelum.CElement
import net.echonolix.caelum.CType
import net.echonolix.caelum.CaelumCodegenContext
import net.echonolix.caelum.codegen.c.adapter.*
import java.nio.file.Path
import net.echonolix.caelum.codegen.c.adapter.CBasicType as CAstBasicType
import net.echonolix.caelum.codegen.c.adapter.CType as CAstType

class CCodeGenContext(basePkgName: String, outputDir: Path, val cAstContext: CAstContext) :
    CaelumCodegenContext(basePkgName, outputDir) {
    override fun resolvePackageName(element: CElement): String {
        return basePkgName
    }

    private fun resolveCType(type: CAstType): CType {
        return when (type) {
            is CAstBasicType -> {
                type.sizeSpecifiers.fold(
                    when (type.basic) {
                        CPrimitiveType.Void -> CBasicType.void
                        CPrimitiveType.Char -> CBasicType.char
                        CPrimitiveType.Bool -> throw UnsupportedOperationException("bool")
                        CPrimitiveType.Int -> CBasicType.int
                        CPrimitiveType.Float -> CBasicType.float
                        CPrimitiveType.Double -> CBasicType.double
                        else -> CBasicType.int
                    }, { acc, specifier ->
                        when (specifier) {
                            CSizeSpecifier.Long -> acc.toLong()
                            CSizeSpecifier.Short -> acc.toShort()
                            CSizeSpecifier.Signed -> acc.toSigned()
                            CSizeSpecifier.Unsigned -> acc.toUnsigned()
                        }
                    }
                ).cType
            }
            is CArrayType -> {
                if (type.size == null) {
                    CType.Array(resolveCType(type.type))
                } else {
                    CType.Array.Sized(resolveCType(type.type), resolveExpression(type.size!!))
                }
            }
            is CPointer -> {
                CType.Pointer { resolveCType(type.pointee) }
            }
            is Identifier -> {
                return resolveElement<CType>(type.name)
            }
            else -> throw UnsupportedOperationException("Unsupported type: $type")
        }
    }

    private fun resolveFunction(name: String, function: CFunction): CType.Function {
        val params = if (function.params.isEmpty()
            || (function.params[0].type as? CAstBasicType)?.basic == CPrimitiveType.Void
        ) {
            emptyList()
        } else {
            function.params.map {
                CType.Function.Parameter(it.name!!.name, resolveCType(it.type))
            }
        }
        return CType.Function(
            name,
            resolveCType(function.returnType),
            params
        )
    }

    private fun resolveStruct(name: String, struct: CStruct): CType.Struct {
        return CType.Struct(
            name,
            struct.fields.map {
                CType.Group.Member(
                    it.id.name,
                    resolveCType(it.type),
                )
            }
        )
    }

    private fun resolveUnion(name: String, struct: CUnion): CType.Union {
        return CType.Union(
            name,
            struct.fields.map {
                CType.Group.Member(
                    it.id.name,
                    resolveCType(it.type),
                )
            }
        )
    }

    private fun resolveTypedef(name: String, type: CAstType): CType {
        return when (type) {
            is CPointer -> {
                val pointee = type.pointee
                if (pointee is CFunction) {
                    val func = resolveFunction(name, pointee)
                    addToCache(name, func)
                    val funcPtr = CType.FunctionPointer(func)
                    funcPtr
                } else {
                    val pointer = CType.Pointer { resolveCType(pointee) }
                    addToCache(name, pointer)
                    pointer
                }
            }
            else -> throw UnsupportedOperationException("Unsupported type: $type")
        }
    }

    override fun resolveElementImpl(cElementStr: String): CElement {
        cAstContext.structs[cElementStr]?.let {
            return resolveStruct(cElementStr, it)
        }

        cAstContext.unions[cElementStr]?.let {
            return resolveUnion(cElementStr, it)
        }

        cAstContext.typedefs[cElementStr]?.let {
            return resolveTypedef(cElementStr, it)
        }

        cAstContext.functions[cElementStr]?.let {
            return resolveFunction(cElementStr, it)
        }

        throw IllegalStateException("Cannot resolve type: $cElementStr")
    }
}
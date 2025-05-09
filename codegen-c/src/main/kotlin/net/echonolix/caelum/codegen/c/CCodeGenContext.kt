package net.echonolix.caelum.codegen.c

import c.lang.ASTNumberValue
import c.lang.CPrimitiveType
import c.lang.CSizeSpecifier
import com.squareup.kotlinpoet.CodeBlock
import net.echonolix.caelum.codegen.api.*
import net.echonolix.caelum.codegen.api.CBasicType
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.ElementResolver
import net.echonolix.caelum.codegen.api.ctx.resolveTypedElement
import net.echonolix.caelum.codegen.c.adapter.*
import net.echonolix.caelum.codegen.c.adapter.CBasicType as CAstBasicType
import net.echonolix.caelum.codegen.c.adapter.CType as CAstType

class CElementResolver(
    private val cAstContext: CAstContext,
    private val renameMap: Map<String, String>,
) : ElementResolver.Base() {
    lateinit var ctx: CodegenContext

    private val reverseMap = renameMap.entries.associate { (key, value) -> value to key }

    private fun renamed(name: String): String {
        return renameMap[name] ?: name
    }

    private fun resolveCType(type: CAstType): CType {
        return when (type) {
            is CAstBasicType -> {
                type.sizeSpecifiers.fold(
                    when (type.basic) {
                        CPrimitiveType.Void -> CBasicType.void
                        CPrimitiveType.Char -> CBasicType.char
                        CPrimitiveType.Bool -> CBasicType.char
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
                return resolveTypedElement<CType>(renamed(type.name))
            }
            is CStruct -> {
                return resolveTypedElement<CType.Struct>(renamed(type.id!!.name))
            }
            is CEnum -> {
                return resolveTypedElement<CType.Enum>(renamed(type.id!!.name))
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
            function.params.mapIndexed { i, it ->
                CType.Function.Parameter(it.name?.name ?: "p$i", resolveCType(it.type))
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
        when (type) {
            is CPointer -> {
                val pointee = type.pointee
                if (pointee is CFunction) {
                    val func = resolveFunction(name, pointee)
                    val funcPtr = CType.FunctionPointer(func)
                    return funcPtr
                }
            }
            is CEnum -> {
                return resolveEnum(name, type)
            }
            else -> {}
        }

        return CType.TypeDef(
            name,
            resolveCType(type)
        )
    }

    private fun ASTNumberValue.asKotlinCode(): CodeBlock {
        return when (this) {
            is ASTNumberValue.Binary -> CodeBlock.of("%L %L %L", left.asKotlinCode(), op.ktRep, right.asKotlinCode())
            is ASTNumberValue.Literal -> CodeBlock.of("%L", literal)
            is ASTNumberValue.Paraenthesized -> CodeBlock.of("(%L)", v.asKotlinCode())
            is ASTNumberValue.Ref -> this.asKotlinCode()
            is ASTNumberValue.Unary -> CodeBlock.of("%L%L", op.ktRep, v.asKotlinCode())
        }
    }

    private fun ASTNumberValue.Ref.asKotlinCode(): CodeBlock {
        return with(ctx) {
            CodeBlock.of("%M", resolveTypedElement<CTopLevelConst>(enumName).memberName())
        }
    }

    private fun resolveEnum(name: String, cEnum: CEnum): CType.Enum {
        val cTypeEnum = CType.Enum(name, CBasicType.int32_t.cType)

        cEnum.enumerators.forEach {
            val entryName = it.id.name
            cTypeEnum.entries[entryName] = cTypeEnum.Entry(
                entryName,
                CExpression.Const(CBasicType.int32_t, it.value.asKotlinCode())
            )
        }

        return cTypeEnum
    }

    private fun resolveConst(name: String, type: CBasicType<*>, value: ASTNumberValue): CTopLevelConst {
        return CTopLevelConst(
            name,
            CExpression.Const(type, value.asKotlinCode())
        )
    }

    override fun resolveElementImpl(input: String): CElement {
        return run {
            cAstContext.typedefs[input]?.let {
                return@run resolveTypedef(input, it)
            }

            cAstContext.consts[input]?.let {
                return@run resolveConst(input, (resolveCType(it.type) as CType.BasicType).baseType, it.value)
            }

            cAstContext.enums[input]?.let {
                return@run resolveEnum(input, it)
            }

            cAstContext.structs[input]?.let {
                return@run resolveStruct(input, it)
            }

            cAstContext.unions[input]?.let {
                return@run resolveUnion(input, it)
            }

            cAstContext.globalEnums[input]?.let {
                return@run resolveConst(input, CBasicType.int, it.value)
            }

            cAstContext.functions[input]?.let {
                val function = resolveFunction(input, it)
                function.tags.set(GlobalFunctionTag)
                return@run function
            }

            throw IllegalStateException("Cannot resolve type: $input")
        }.also {
            val originalName = reverseMap[it.name]
            if (originalName != null && originalName != it.name) {
                it.tags.set(OriginalNameTag(originalName))
            }
        }
    }
}
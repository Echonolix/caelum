package net.echonolix.caelum.codegen.api.generator

import com.squareup.kotlinpoet.*
import net.echonolix.caelum.codegen.api.*
import net.echonolix.caelum.codegen.api.ctx.CodegenContext

public open class FuncGenerator(
    ctx: CodegenContext,
    element: CType.Function
) : Generator<CType.Function>(ctx, element) {
    public val returnType: CType = element.returnType

    public fun generateFuncDesc(): PropertySpec {
        return with(ctx) {
            val nullCodeBlock = CodeBlock.Companion.of("null")
            fun typeDescriptorCodeBlock(type: CType): CodeBlock {
                return type.typeDescriptorTypeName()?.let {
                    CodeBlock.Companion.of("%T", it)
                } ?: nullCodeBlock
            }

            val fdParams = mutableListOf(
                typeDescriptorCodeBlock(returnType)
            )
            element.parameters.mapTo(fdParams) {
                typeDescriptorCodeBlock(it.type)
            }
            PropertySpec.builder(element.funcDescPropertyName(), CaelumCodegenHelper.functionDescriptorCName)
                .addModifiers(KModifier.INTERNAL)
                .initializer(
                    CodeBlock.builder()
                        .add("%M(", CaelumCodegenHelper.functionDescriptorOfMemberName)
                        .add("\n")
                        .indent()
                        .add(fdParams.joinToCode(",\n"))
                        .add("\n")
                        .unindent()
                        .add(")")
                        .build()
                )
                .build()
        }
    }

    public fun generateGlobalFunc(): List<Any> {
        return with(ctx) {
            if (!element.tags.has<GlobalFunctionTag>()) {
                emptyList()
            } else {
                val outputList = mutableListOf<Any>()
                outputList +=
                    PropertySpec.builder(element.funcMethodHandlePropertyName(), CaelumCodegenHelper.methodHandleCName)
                        .addModifiers(KModifier.PRIVATE)
                        .initializer(
                            "%M(%M(%S), %N)!!",
                            CaelumCodegenHelper.downcallHandleOfMemberName,
                            CaelumCodegenHelper.findSymbolMemberName,
                            element.funcName(),
                            element.funcDescPropertyName()
                        )
                        .build()

                val func1 = FunSpec.builder(element.funcName())
                func1.addModifiers(KModifier.PUBLIC)
                func1.returns(element.returnType.ktApiType())
                func1.addParameters(element.parameters.toParamSpecs(true) { it.type.ktApiType() })
                val funcCode = CodeBlock.builder()
                val typeDescriptorTypeName = element.returnType.typeDescriptorTypeName()
                funcCode.add("return ")
                if (typeDescriptorTypeName != null) {
                    funcCode.add(fromNativeDataCodeBlock(element.returnType))
                }
                funcCode.add("%N.invokeExact(\n", element.funcMethodHandlePropertyName())

                val callParams = element.parameters.toParameterCode(mutableListOf())
                funcCode.indent()
                funcCode.add(callParams.joinToCode(",\n"))
                funcCode.unindent()
                funcCode.add("\n) as %T", element.returnType.nativeType())
                if (typeDescriptorTypeName != null) {
                    funcCode.add(")")
                }
                func1.addCode(funcCode.build())
                outputList += func1.build()

                if (element.parameters.any { it.type is CType.FunctionPointer }) {
                    val funcPtrOverride = FunSpec.builder(element.funcName())
                    funcPtrOverride.addModifiers(KModifier.PUBLIC)
                    funcPtrOverride.returns(element.returnType.ktApiType())
                    funcPtrOverride.addParameters(element.parameters.toParamSpecs(true) {
                        if (it.type is CType.FunctionPointer) {
                            it.type.elementType.ktApiType()
                        } else {
                            it.type.ktApiType()
                        }
                    })
                    val funcCode = CodeBlock.builder()
                    val typeDescriptorTypeName = element.returnType.typeDescriptorTypeName()
                    funcCode.add("return ")
                    if (typeDescriptorTypeName != null) {
                        funcCode.add(fromNativeDataCodeBlock(element.returnType))
                    }
                    funcCode.add("%N.invokeExact(\n", element.funcMethodHandlePropertyName())

                    val callParams = element.parameters.toParameterCode(mutableListOf()) {
                        if (it.type is CType.FunctionPointer) {
                            it.type.elementType.typeDescriptorTypeName()!!
                        } else {
                            it.type.typeDescriptorTypeName()!!
                        }
                    }
                    funcCode.indent()
                    funcCode.add(callParams.joinToCode(",\n"))
                    funcCode.unindent()
                    funcCode.add("\n) as %T", element.returnType.nativeType())
                    if (typeDescriptorTypeName != null) {
                        funcCode.add(")")
                    }
                    funcPtrOverride.addCode(funcCode.build())
                    outputList += funcPtrOverride.build()
                }

                outputList
            }
        }
    }

    public final override fun generate(): FileSpec.Builder {
        throw UnsupportedOperationException()
    }
}
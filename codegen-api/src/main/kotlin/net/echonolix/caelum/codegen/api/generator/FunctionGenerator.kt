package net.echonolix.caelum.codegen.api.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.codegen.api.*
import net.echonolix.caelum.codegen.api.ctx.CodegenContext

public open class FunctionGenerator(
    ctx: CodegenContext,
    element: CType.Function
) : Generator<CType.Function>(ctx, element) {
    public val returnType: CType = element.returnType

    context(ctx: CodegenContext)
    protected open fun toKtType(type: CType.Function.Parameter): TypeName {
        return type.type.ktApiType()
    }

    context(ctx: CodegenContext)
    protected open fun toNativeType(type: CType.Function.Parameter): TypeName {
        return type.type.nativeType()
    }

    context(ctx: CodegenContext)
    protected open fun functionBaseCName(): ClassName {
        return CaelumCodegenHelper.NFunction.cName
    }

    context(ctx: CodegenContext)
    protected open fun functionTypeDescriptorBaseCName(): ClassName {
        return CaelumCodegenHelper.NFunction.typeDescriptorCName
    }

    context(ctx: CodegenContext)
    protected open fun nativeName(): String {
        return element.tags.getOrNull<OriginalNameTag>()?.name ?: element.name
    }

    context(ctx: CodegenContext)
    protected open fun buildFunInterfaceType(): TypeSpec.Builder {
        val funInterfaceType = TypeSpec.Companion.funInterfaceBuilder(thisCName)
        funInterfaceType.addSuperinterface(functionBaseCName())
        funInterfaceType.addProperty(
            PropertySpec.Companion.builder(
                "typeDescriptor",
                functionTypeDescriptorBaseCName().parameterizedBy(thisCName)
            )
                .addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.Companion.getterBuilder()
                        .addCode("return TypeDescriptor")
                        .build()
                )
                .build()
        )

        val invokeFunc = FunSpec.Companion.builder("invoke")
        invokeFunc.addModifiers(KModifier.OPERATOR, KModifier.ABSTRACT)
        invokeFunc.returns(returnType.ktApiType())
        invokeFunc.addParameters(element.parameters.toParamSpecs(true) { toKtType(it) })
        funInterfaceType.addFunction(invokeFunc.build())

        val invokeNativeFunc = FunSpec.Companion.builder("invokeNative")
        invokeNativeFunc.returns(returnType.nativeType())
        invokeNativeFunc.addParameters(element.parameters.toParamSpecs(false) { toNativeType(it) })
        val invokeNativeCode = CodeBlock.Companion.builder()
        val rTypeDesc = returnType.typeDescriptorTypeName()
        invokeNativeCode.add("return ")
        if (rTypeDesc != null) {
            invokeNativeCode.add("%T.toNativeData(\n", rTypeDesc)
            invokeNativeCode.indent()
        }
        invokeNativeCode.add("invoke(\n")
        invokeNativeCode.indent()
        invokeNativeCode.add(element.parameters.map {
            CodeBlock.Companion.builder()
                .add(fromNativeDataCodeBlock(it.type))
                .add("%N)", it.name)
                .build()
        }.joinToCode(",\n"))
        invokeNativeCode.unindent()
        invokeNativeCode.add("\n)")
        if (rTypeDesc != null) {
            invokeNativeCode.unindent()
            invokeNativeCode.add("\n)")
        }
        invokeNativeFunc.addCode(invokeNativeCode.build())
        funInterfaceType.addFunction(invokeNativeFunc.build())

        return funInterfaceType
    }

    context(ctx: CodegenContext)
    protected open fun buildTypeDescriptorCompanionType(): TypeSpec.Builder {
        val companionType = TypeSpec.Companion.companionObjectBuilder("TypeDescriptor")
        companionType.superclass(functionTypeDescriptorBaseCName().parameterizedBy(thisCName))
        val nullCodeBlock = CodeBlock.Companion.of("null")
        fun typeDescriptorCodeBlock(type: CType): CodeBlock {
            return type.typeDescriptorTypeName()?.let {
                CodeBlock.Companion.of("%T", it)
            } ?: nullCodeBlock
        }

        val superParameters = mutableListOf(
            CodeBlock.Companion.of("%S", nativeName()),
            CodeBlock.Companion.of("%T::invokeNative", thisCName),
            typeDescriptorCodeBlock(returnType)
        )
        element.parameters.mapTo(superParameters) {
            typeDescriptorCodeBlock(it.type)
        }
        companionType.addSuperclassConstructorParameter(
            CodeBlock.Companion.builder()
                .add("\n")
                .indent()
                .add(superParameters.joinToCode(",\n"))
                .add("\n")
                .unindent()
                .build()
        )
        companionType.addFunction(
            FunSpec.Companion.builder("fromNativeData")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter(
                    "value",
                    CaelumCodegenHelper.memorySegmentCName
                )
                .returns(thisCName)
                .addStatement("return Impl(downcallHandle(value))")
                .build()
        )
        return companionType
    }

    context(ctx: CodegenContext)
    protected open fun buildImplType(): TypeSpec.Builder {
        val rTypeDesc = returnType.typeDescriptorTypeName()

        val implType = TypeSpec.Companion.classBuilder("Impl")
        implType.addModifiers(KModifier.PRIVATE)
        implType.addSuperinterface(thisCName)
        implType.superclass(CaelumCodegenHelper.NFunction.implCName)
        implType.addSuperclassConstructorParameter("funcHandle")
        implType.primaryConstructor(
            FunSpec.Companion.constructorBuilder()
                .addParameter("funcHandle", CaelumCodegenHelper.methodHandleCName)
                .build()
        )

        val implInvokeFunc = FunSpec.Companion.builder("invoke")
        implInvokeFunc.addModifiers(KModifier.OVERRIDE)
        implInvokeFunc.returns(returnType.ktApiType())
        implInvokeFunc.addParameters(element.parameters.toParamSpecs(false) { toKtType(it) })
        val implInvokeCode = CodeBlock.Companion.builder()
        implInvokeCode.add("return ")
        if (rTypeDesc != null) {
            implInvokeCode.add(fromNativeDataCodeBlock(returnType))
            implInvokeCode.add("\n")
            implInvokeCode.indent()
        }
        implInvokeCode.add("invokeNative(\n")
        implInvokeCode.indent()
        implInvokeCode.add(element.parameters.map {
            CodeBlock.Companion.of("%T.toNativeData(%N)", it.type.typeDescriptorTypeName()!!, it.name)
        }.joinToCode(",\n"))
        implInvokeCode.unindent()
        implInvokeCode.add("\n)")
        if (rTypeDesc != null) {
            implInvokeCode.unindent()
            implInvokeCode.add("\n)")
        }
        implInvokeFunc.addCode(implInvokeCode.build())
        implType.addFunction(implInvokeFunc.build())


        val implInvokeNativeFunc = FunSpec.Companion.builder("invokeNative")
        implInvokeNativeFunc.addModifiers(KModifier.OVERRIDE)
        implInvokeNativeFunc.returns(returnType.nativeType())
        implInvokeNativeFunc.addParameters(element.parameters.toParamSpecs(false) { toNativeType(it) })
        val implInvokeNativeCode = CodeBlock.Companion.builder()
        implInvokeNativeCode.add("return funcHandle.invokeExact(\n")
        implInvokeNativeCode.indent()
        implInvokeNativeCode.add(element.parameters.map {
            CodeBlock.Companion.of("%N", it.name)
        }.joinToCode(",\n"))
        implInvokeNativeCode.unindent()
        implInvokeNativeCode.add("\n) as %T", returnType.nativeType())
        implInvokeNativeFunc.addCode(implInvokeNativeCode.build())
        implType.addFunction(implInvokeNativeFunc.build())

        return implType
    }

    public final override fun generate(): FileSpec.Builder {
        with(ctx) {
            val funInterfaceType = buildFunInterfaceType()
            val typeDescriptorCompanionType = buildTypeDescriptorCompanionType()
            val implType = buildImplType()
            typeDescriptorCompanionType.addType(implType.build())
            funInterfaceType.addType(typeDescriptorCompanionType.build())
            val file = FileSpec.Companion.builder(thisCName)
            if (element.tags.has<GlobalFunctionTag>()) {
                file.addProperty(
                    PropertySpec.builder(nativeName(), thisCName)
                        .addModifiers(KModifier.PUBLIC)
                        .initializer(
                            "%T.fromNativeData(%T.findSymbol(%S))",
                            thisCName,
                            CaelumCodegenHelper.helperCName,
                            nativeName()
                        )
                        .build()
                )
            }
            file.addType(funInterfaceType.build())
            return file
        }
    }

    context(ctx: CodegenContext)
    private fun fromNativeDataCodeBlock(type: CType): CodeBlock {
        return if (type is CType.Pointer && type.elementType.typeDescriptorTypeName() == null) {
            CodeBlock.Companion.of(
                "%T.fromNativeData<%T>(",
                type.typeDescriptorTypeName()!!,
                CBasicType.char.caelumCoreTypeName
            )
        } else {
            CodeBlock.Companion.of("%T.fromNativeData(", type.typeDescriptorTypeName()!!)
        }
    }
}
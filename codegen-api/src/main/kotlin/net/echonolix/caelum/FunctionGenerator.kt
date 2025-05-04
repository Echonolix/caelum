package net.echonolix.caelum

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

public open class FunctionGenerator<CTX : CaelumCodegenContext>(
    public val ctx: CTX,
    public val funcType: CType.Function
) {
    public val thisCname: ClassName = with(ctx) { funcType.className() }
    public val returnType: CType = funcType.returnType

    context(ctx: CTX)
    protected open fun toKtType(type: CType.Function.Parameter): TypeName {
        return type.type.ktApiType()
    }

    context(ctx: CTX)
    protected open fun toNativeType(type: CType.Function.Parameter): TypeName {
        return type.type.nativeType()
    }

    context(ctx: CTX)
    protected open fun functionBaseCName(): ClassName {
        return CaelumCodegenHelper.functionCname
    }

    context(ctx: CTX)
    protected open fun functionTypeDescriptorBaseCName(): ClassName {
        return CaelumCodegenHelper.functionTypeDescriptorImplCname
    }

    context(ctx: CTX)
    protected open fun nativeName(): String {
        return funcType.name
    }

    context(ctx: CTX)
    protected open fun buildFunInterfaceType(): TypeSpec.Builder {
        val funInterfaceType = TypeSpec.funInterfaceBuilder(thisCname)
        funInterfaceType.addSuperinterface(functionBaseCName())
        funInterfaceType.addProperty(
            PropertySpec.builder(
                "typeDescriptor",
                functionTypeDescriptorBaseCName().parameterizedBy(thisCname)
            )
                .addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addCode("return TypeDescriptor")
                        .build()
                )
                .build()
        )

        val invokeFunc = FunSpec.builder("invoke")
        invokeFunc.addModifiers(KModifier.OPERATOR, KModifier.ABSTRACT)
        invokeFunc.returns(returnType.ktApiType())
        invokeFunc.addParameters(funcType.parameters.toParamSpecs(true) { toKtType(it) })
        funInterfaceType.addFunction(invokeFunc.build())

        val invokeNativeFunc = FunSpec.builder("invokeNative")
        invokeNativeFunc.returns(returnType.nativeType())
        invokeNativeFunc.addParameters(funcType.parameters.toParamSpecs(false) { toNativeType(it) })
        val invokeNativeCode = CodeBlock.builder()
        val rTypeDesc = returnType.typeDescriptorTypeName()
        invokeNativeCode.add("return ")
        if (rTypeDesc != null) {
            invokeNativeCode.add("%T.toNativeData(\n", rTypeDesc)
            invokeNativeCode.indent()
        }
        invokeNativeCode.add("invoke(\n")
        invokeNativeCode.indent()
        invokeNativeCode.add(funcType.parameters.map {
            CodeBlock.builder()
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

    context(ctx: CTX)
    protected open fun buildTypeDescriptorCompanionType(): TypeSpec.Builder {
        val companionType = TypeSpec.companionObjectBuilder("TypeDescriptor")
        companionType.superclass(functionTypeDescriptorBaseCName().parameterizedBy(thisCname))
        val nullCodeBlock = CodeBlock.of("null")
        fun typeDescriptorCodeBlock(type: CType): CodeBlock {
            return type.typeDescriptorTypeName()?.let {
                CodeBlock.of("%T", it)
            } ?: nullCodeBlock
        }

        val superParameters = mutableListOf(
            CodeBlock.of("%S", nativeName()),
            CodeBlock.of(
                "%T.lookup().unreflect(%T::invokeNative.%M)",
                CaelumCodegenHelper.methodHandlesCname,
                thisCname,
                CaelumCodegenHelper.javaMethodMemberName
            ),
            typeDescriptorCodeBlock(returnType)
        )
        funcType.parameters.mapTo(superParameters) {
            typeDescriptorCodeBlock(it.type)
        }
        companionType.addSuperclassConstructorParameter(
            CodeBlock.builder()
                .add("\n")
                .indent()
                .add(superParameters.joinToCode(",\n"))
                .add("\n")
                .unindent()
                .build()
        )
        companionType.addFunction(
            FunSpec.builder("fromNativeData")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter(
                    "value",
                    CaelumCodegenHelper.memorySegmentCname
                )
                .returns(thisCname)
                .addStatement("return Impl(downcallHandle(value))")
                .build()
        )
        return companionType
    }

    context(ctx: CTX)
    protected open fun buildImplType(): TypeSpec.Builder {
        val rTypeDesc = returnType.typeDescriptorTypeName()

        val implType = TypeSpec.classBuilder("Impl")
        implType.addModifiers(KModifier.PRIVATE)
        implType.addSuperinterface(thisCname)
        implType.superclass(CaelumCodegenHelper.functionImplCname)
        implType.addSuperclassConstructorParameter("funcHandle")
        implType.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter("funcHandle", CaelumCodegenHelper.methodHandleCname)
                .build()
        )

        val implInvokeFunc = FunSpec.builder("invoke")
        implInvokeFunc.addModifiers(KModifier.OVERRIDE)
        implInvokeFunc.returns(returnType.ktApiType())
        implInvokeFunc.addParameters(funcType.parameters.toParamSpecs(false) { toKtType(it) })
        val implInvokeCode = CodeBlock.builder()
        implInvokeCode.add("return ")
        if (rTypeDesc != null) {
            implInvokeCode.add(fromNativeDataCodeBlock(returnType))
            implInvokeCode.add("\n")
            implInvokeCode.indent()
        }
        implInvokeCode.add("invokeNative(\n")
        implInvokeCode.indent()
        implInvokeCode.add(funcType.parameters.map {
            CodeBlock.of("%T.toNativeData(%N)", it.type.typeDescriptorTypeName()!!, it.name)
        }.joinToCode(",\n"))
        implInvokeCode.unindent()
        implInvokeCode.add("\n)")
        if (rTypeDesc != null) {
            implInvokeCode.unindent()
            implInvokeCode.add("\n)")
        }
        implInvokeFunc.addCode(implInvokeCode.build())
        implType.addFunction(implInvokeFunc.build())


        val implInvokeNativeFunc = FunSpec.builder("invokeNative")
        implInvokeNativeFunc.addModifiers(KModifier.OVERRIDE)
        implInvokeNativeFunc.returns(returnType.nativeType())
        implInvokeNativeFunc.addParameters(funcType.parameters.toParamSpecs(false) { toNativeType(it) })
        val implInvokeNativeCode = CodeBlock.builder()
        implInvokeNativeCode.add("return funcHandle.invokeExact(\n")
        implInvokeNativeCode.indent()
        implInvokeNativeCode.add(funcType.parameters.map {
            CodeBlock.of("%N", it.name)
        }.joinToCode(",\n"))
        implInvokeNativeCode.unindent()
        implInvokeNativeCode.add("\n) as %T", returnType.nativeType())
        implInvokeNativeFunc.addCode(implInvokeNativeCode.build())
        implType.addFunction(implInvokeNativeFunc.build())

        return implType
    }

    public fun build(): FileSpec.Builder {
        with(ctx) {
            val funInterfaceType = buildFunInterfaceType()
            val typeDescriptorCompanionType = buildTypeDescriptorCompanionType()
            val implType = buildImplType()
            typeDescriptorCompanionType.addType(implType.build())
            funInterfaceType.addType(typeDescriptorCompanionType.build())
            val file = FileSpec.builder(thisCname)
            file.addType(funInterfaceType.build())
            return file
        }
    }

    context(ctx: CTX)
    private fun fromNativeDataCodeBlock(type: CType): CodeBlock {
        return if (type is CType.Pointer && type.elementType.typeDescriptorTypeName() == null) {
            CodeBlock.of(
                "%T.fromNativeData<%T>(",
                type.typeDescriptorTypeName()!!,
                CBasicType.char.caelumCoreTypeName
            )
        } else {
            CodeBlock.of("%T.fromNativeData(", type.typeDescriptorTypeName()!!)
        }
    }
}
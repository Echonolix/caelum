package net.echonolix.caelum.codegen.api.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.joinToCode
import net.echonolix.caelum.codegen.api.CBasicType
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.CaelumCodegenHelper
import net.echonolix.caelum.codegen.api.toParamSpecs

public open class FunctionGenerator(
    ctx: CodegenContext,
    element: CType.Function
): Generator<CType.Function>(ctx, element) {
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
        return CaelumCodegenHelper.functionCname
    }

    context(ctx: CodegenContext)
    protected open fun functionTypeDescriptorBaseCName(): ClassName {
        return CaelumCodegenHelper.functionTypeDescriptorImplCname
    }

    context(ctx: CodegenContext)
    protected open fun nativeName(): String {
        return element.name
    }

    context(ctx: CodegenContext)
    protected open fun buildFunInterfaceType(): TypeSpec.Builder {
        val funInterfaceType = TypeSpec.Companion.funInterfaceBuilder(thisCname)
        funInterfaceType.addSuperinterface(functionBaseCName())
        funInterfaceType.addProperty(
            PropertySpec.Companion.builder(
                "typeDescriptor",
                functionTypeDescriptorBaseCName().parameterizedBy(thisCname)
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
        companionType.superclass(functionTypeDescriptorBaseCName().parameterizedBy(thisCname))
        val nullCodeBlock = CodeBlock.Companion.of("null")
        fun typeDescriptorCodeBlock(type: CType): CodeBlock {
            return type.typeDescriptorTypeName()?.let {
                CodeBlock.Companion.of("%T", it)
            } ?: nullCodeBlock
        }

        val superParameters = mutableListOf(
            CodeBlock.Companion.of("%S", nativeName()),
            CodeBlock.Companion.of(
                "%T.lookup().unreflect(%T::invokeNative.%M)",
                CaelumCodegenHelper.methodHandlesCname,
                thisCname,
                CaelumCodegenHelper.javaMethodMemberName
            ),
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
                    CaelumCodegenHelper.memorySegmentCname
                )
                .returns(thisCname)
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
        implType.addSuperinterface(thisCname)
        implType.superclass(CaelumCodegenHelper.functionImplCname)
        implType.addSuperclassConstructorParameter("funcHandle")
        implType.primaryConstructor(
            FunSpec.Companion.constructorBuilder()
                .addParameter("funcHandle", CaelumCodegenHelper.methodHandleCname)
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

    public final override fun build(): FileSpec.Builder {
        with(ctx) {
            val funInterfaceType = buildFunInterfaceType()
            val typeDescriptorCompanionType = buildTypeDescriptorCompanionType()
            val implType = buildImplType()
            typeDescriptorCompanionType.addType(implType.build())
            funInterfaceType.addType(typeDescriptorCompanionType.build())
            val file = FileSpec.Companion.builder(thisCname)
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
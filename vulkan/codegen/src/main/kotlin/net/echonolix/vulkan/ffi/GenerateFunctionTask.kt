package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.ktffi.CBasicType
import net.echonolix.ktffi.CType
import net.echonolix.ktffi.CTypeName
import net.echonolix.ktffi.KTFFICodegenHelper

class GenerateFunctionTask(ctx: VKFFICodeGenContext) : VKFFITask<Unit>(ctx) {
    override fun VKFFICodeGenContext.compute() {
        ctx.filterTypeStream<CType.Function>().map { (_, funcType) -> genFunc(funcType) }.forEach(ctx::writeOutput)
    }

    private fun VKFFICodeGenContext.genFunc(funcType: CType.Function): FileSpec.Builder {
        val thisCname = funcType.className()
        val returnType = funcType.returnType
        val funInterfaceType = TypeSpec.funInterfaceBuilder(thisCname)
        funInterfaceType.addSuperinterface(KTFFICodegenHelper.functionCname)
        funInterfaceType.addProperty(
            PropertySpec.builder(
                "typeDescriptor",
                KTFFICodegenHelper.functionTypeDescriptorImplCname.parameterizedBy(thisCname)
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
        invokeFunc.addParameters(funcType.parameters.map {
            ParameterSpec.builder(it.name, it.type.ktApiType())
                .addAnnotation(
                    AnnotationSpec.builder(CTypeName::class)
                        .addMember("%S", it.type.name)
                        .build()
                )
                .build()
        })
        funInterfaceType.addFunction(invokeFunc.build())

        fun fromNativeDataCodeBlock(type: CType): CodeBlock {
            return if (type is CType.Pointer && type.elementType.typeDescriptorTypeName() == null) {
                CodeBlock.of(
                    "%T.fromNativeData<%T>(",
                    type.typeDescriptorTypeName()!!,
                    CBasicType.char.ktffiTypeName
                )
            } else {
                CodeBlock.of("%T.fromNativeData(", type.typeDescriptorTypeName()!!)
            }
        }

        val invokeNativeFunc = FunSpec.builder("invokeNative")
        invokeNativeFunc.returns(returnType.nativeType())
        invokeNativeFunc.addParameters(funcType.parameters.map {
            ParameterSpec.builder(it.name, it.type.nativeType())
                .addAnnotation(
                    AnnotationSpec.builder(CTypeName::class)
                        .addMember("%S", it.type.name)
                        .build()
                )
                .build()
        })
        val invokeNativeCode = CodeBlock.builder()
        val rTypeDesc = returnType.typeDescriptorTypeName()
        invokeNativeCode.addStatement("")
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

        val companion = TypeSpec.companionObjectBuilder("TypeDescriptor")
        companion.superclass(KTFFICodegenHelper.functionTypeDescriptorImplCname.parameterizedBy(thisCname))
        val nullCodeBlock = CodeBlock.of("null")
        fun typeDescriptorCodeBlock(type: CType): CodeBlock {
            return type.typeDescriptorTypeName()?.let {
                CodeBlock.of("%T", it)
            } ?: nullCodeBlock
        }

        val superParameters = mutableListOf(
            CodeBlock.of(
                "%T.lookup().unreflect(%T::invokeNative.%M)",
                KTFFICodegenHelper.methodHandlesCname,
                thisCname,
                KTFFICodegenHelper.javaMethodMemberName
            ),
            typeDescriptorCodeBlock(returnType)
        )
        funcType.parameters.mapTo(superParameters) {
            typeDescriptorCodeBlock(it.type)
        }
        companion.addSuperclassConstructorParameter(
            CodeBlock.builder()
                .add("\n")
                .indent()
                .add(superParameters.joinToCode(",\n"))
                .add("\n")
                .unindent()
                .build()
        )
        companion.addFunction(
            FunSpec.builder("fromNativeData")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter(
                    "value",
                    KTFFICodegenHelper.memorySegmentCname
                )
                .returns(thisCname)
                .addStatement("return Impl(downcallHandle(value))")
                .build()
        )
        val implType = TypeSpec.classBuilder("Impl")
        implType.addModifiers(KModifier.PRIVATE)
        implType.addSuperinterface(thisCname)
        implType.superclass(KTFFICodegenHelper.functionImplCname)
        implType.addSuperclassConstructorParameter("funcHandle")
        implType.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter("funcHandle", KTFFICodegenHelper.methodHandleCname)
                .build()
        )

        val implInvokeFunc = FunSpec.builder("invoke")
        implInvokeFunc.addModifiers(KModifier.OVERRIDE)
        implInvokeFunc.returns(returnType.ktApiType())
        implInvokeFunc.addParameters(funcType.parameters.map {
            ParameterSpec.builder(it.name, it.type.ktApiType()).build()
        })
        val implInvokeCode = CodeBlock.builder()
        implInvokeCode.addStatement("")
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
        implInvokeNativeFunc.addParameters(funcType.parameters.map {
            ParameterSpec.builder(it.name, it.type.nativeType()).build()
        })
        val implInvokeNativeCode = CodeBlock.builder()
        implInvokeNativeCode.addStatement("")
        implInvokeNativeCode.add("return funcHandle.invokeExact(\n")
        implInvokeNativeCode.indent()
        implInvokeNativeCode.add(funcType.parameters.map {
            CodeBlock.of("%N", it.name)
        }.joinToCode(",\n"))
        implInvokeNativeCode.unindent()
        implInvokeNativeCode.add("\n) as %T", returnType.nativeType())
        implInvokeNativeFunc.addCode(implInvokeNativeCode.build())
        implType.addFunction(implInvokeNativeFunc.build())


        companion.addType(implType.build())
        funInterfaceType.addType(companion.build())

        val file = FileSpec.builder(thisCname)
        file.addType(funInterfaceType.build())
        return file
    }
}
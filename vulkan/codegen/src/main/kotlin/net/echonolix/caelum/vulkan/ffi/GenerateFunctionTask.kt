package net.echonolix.caelum.vulkan.ffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.CBasicType
import net.echonolix.caelum.CType
import net.echonolix.caelum.CaelumCodegenHelper
import kotlin.io.path.Path

class GenerateFunctionTask(ctx: VulkanCodeGenContext) : VKFFITask<Unit>(ctx) {
    override fun VulkanCodeGenContext.compute() {
        val funcPtrPath = Path("groups")
        ctx.filterTypeStream<CType.Function>()
            .filter { (_, funcType) -> funcType.name.startsWith("VkFuncPtr") }
            .map { (_, funcType) -> genFunc(funcType) }
            .forEach { ctx.writeOutput(funcPtrPath, it) }

        ctx.filterTypeStream<CType.Function>()
            .filter { (_, funcType) -> !funcType.name.startsWith("VkFuncPtr") }
            .map { (_, funcType) -> genFunc(funcType) }
            .partitionWrite("functions")
    }

    private fun VulkanCodeGenContext.genFunc(funcType: CType.Function): FileSpec.Builder {
        val rawName = funcType.tags.get<OriginalFunctionNameTag>()!!.name
        val thisCname = funcType.className()
        val returnType = funcType.returnType
        val funInterfaceType = TypeSpec.funInterfaceBuilder(thisCname)
        funInterfaceType.addSuperinterface(VKFFI.vkFunctionCname)
        funInterfaceType.addProperty(
            PropertySpec.builder(
                "typeDescriptor",
                VKFFI.vkFunctionTypeDescriptorImplCname.parameterizedBy(thisCname)
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
        invokeFunc.addParameters(funcType.parameters.toKtParamSpecs(true))
        funInterfaceType.addFunction(invokeFunc.build())

        fun fromNativeDataCodeBlock(type: CType): CodeBlock {
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

        val invokeNativeFunc = FunSpec.builder("invokeNative")
        invokeNativeFunc.returns(returnType.nativeType())
        invokeNativeFunc.addParameters(funcType.parameters.toNativeParamSpecs(false))
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

        val companion = TypeSpec.companionObjectBuilder("TypeDescriptor")
        companion.superclass(VKFFI.vkFunctionTypeDescriptorImplCname.parameterizedBy(thisCname))
        val nullCodeBlock = CodeBlock.of("null")
        fun typeDescriptorCodeBlock(type: CType): CodeBlock {
            return type.typeDescriptorTypeName()?.let {
                CodeBlock.of("%T", it)
            } ?: nullCodeBlock
        }

        val superParameters = mutableListOf(
            CodeBlock.of("%S", rawName),
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
                    CaelumCodegenHelper.memorySegmentCname
                )
                .returns(thisCname)
                .addStatement("return Impl(downcallHandle(value))")
                .build()
        )
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
        implInvokeFunc.addParameters(funcType.parameters.toKtParamSpecs(false))
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
        implInvokeNativeFunc.addParameters(funcType.parameters.toNativeParamSpecs(false))
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


        companion.addType(implType.build())
        funInterfaceType.addType(companion.build())

        val file = FileSpec.builder(thisCname)
        file.addType(funInterfaceType.build())
        return file
    }
}
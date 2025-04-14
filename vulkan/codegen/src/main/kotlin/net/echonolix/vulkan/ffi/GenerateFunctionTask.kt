package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
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


        val companion = TypeSpec.companionObjectBuilder("TypeDescriptor")
        companion.superclass(KTFFICodegenHelper.functionTypeDescriptorImplCname.parameterizedBy(thisCname))
        val superParameters = mutableListOf(
            CodeBlock.of(
                "%M().unreflect(%T::invoke.%M)",
                KTFFICodegenHelper.handleLookUpMember,
                thisCname,
                KTFFICodegenHelper.javaMethodMemberName
            ),
            if (returnType.name == "void") {
                CodeBlock.of("null")
            } else {
                CodeBlock.of("%T", returnType.typeName())
            }
        )
        funcType.parameters.mapTo(superParameters) {
            val paramType = it.type
            if (paramType is CType.Array || paramType is CType.Pointer) {
                CodeBlock.of("%T", KTFFICodegenHelper.pointerCname)
            } else {
                CodeBlock.of("%T", paramType.typeName())
            }
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
        val implInvokeCode = CodeBlock.builder()
        implInvokeCode.add("return funcHandle.invokeExact(")
        implInvokeCode.add(funcType.parameters.map {
            CodeBlock.of("%N", it.name)
        }.joinToCode(", "))
        implInvokeCode.add(") as %T", returnType.ktApiType())
        implType.addFunction(
            FunSpec.builder("invoke")
                .addAnnotation(
                    AnnotationSpec.builder(Suppress::class)
                        .addMember("%S", "UNCHECKED_CAST")
                        .build()
                )
                .addModifiers(KModifier.OVERRIDE)
                .addParameters(funcType.parameters.map {
                    ParameterSpec.builder(it.name, it.type.ktApiType()).build()
                })
                .addCode(implInvokeCode.build())
                .returns(returnType.ktApiType())
                .build()
        )
        companion.addType(implType.build())
        funInterfaceType.addType(companion.build())

        val file = FileSpec.builder(thisCname)
        file.addType(funInterfaceType.build())
        return file
    }
}
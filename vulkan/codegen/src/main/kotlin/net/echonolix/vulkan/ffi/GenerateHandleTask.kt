package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.ktffi.CBasicType
import net.echonolix.ktffi.CType
import net.echonolix.ktffi.KTFFICodegenHelper
import net.echonolix.ktffi.NativeType
import net.echonolix.ktffi.decap

class GenerateHandleTask(ctx: VKFFICodeGenContext) : VKFFITask<Unit>(ctx) {
    fun CType.Handle.variableName(): String {
        return name.removePrefix("Vk").decap()
    }

    override fun VKFFICodeGenContext.compute() {
        val handles = ctx.filterType<CType.Handle>()
        val typeAlias = GenTypeAliasTask(this, handles).fork()
        val vkTypeDescriptorCname = VKFFI.vkHandleCname.nestedClass("TypeDescriptor")

        val objTypeCname = resolveType("VkObjectType").className()
        val typeVariable = TypeVariableName("T", VKFFI.vkHandleCname)
        val vkHandleFile = FileSpec.builder(VKFFI.vkHandleCname)
            .addType(
                TypeSpec.interfaceBuilder(VKFFI.vkHandleCname)
                    .addSuperinterface(NativeType::class)
                    .addProperty("handle", CBasicType.int64_t.kotlinTypeName)
                    .addProperty("objectType", objTypeCname)
                    .addType(
                        TypeSpec.classBuilder(vkTypeDescriptorCname)
                            .addModifiers(KModifier.ABSTRACT)
                            .addTypeVariable(typeVariable)
                            .superclass(KTFFICodegenHelper.typeDescriptorImplCname.parameterizedBy(typeVariable))
                            .addSuperclassConstructorParameter("%M", CBasicType.int64_t.valueLayoutMember)
                            .build()
                    )
                    .build()
            )
        ctx.writeOutput(vkHandleFile)

        handles.parallelStream()
            .filter { (name, dstType) -> name == dstType.name }
            .map { (_, type) ->
                type as VkHandle
                val thisCname = type.className()
                val thisTypeDescriptor = KTFFICodegenHelper.typeDescriptorCname.parameterizedBy(thisCname)
                FileSpec.builder(thisCname)
                    .addType(
                        TypeSpec.interfaceBuilder(thisCname)
                            .addSuperinterface(VKFFI.vkHandleCname)
                            .apply {
                                type.parent?.className()?.nestedClass("Child")?.let {
                                    addSuperinterface(it)
                                }
                            }
                            .addProperty(
                                PropertySpec.builder("objectType", objTypeCname)
                                    .addModifiers(KModifier.OVERRIDE)
                                    .getter(
                                        FunSpec.getterBuilder()
                                            .addStatement("return %T.%N", objTypeCname, type.objectTypeEnum.name)
                                            .build()
                                    )
                                    .build()
                            )
                            .addProperty(
                                PropertySpec.builder("typeDescriptor", thisTypeDescriptor)
                                    .addModifiers(KModifier.OVERRIDE)
                                    .getter(
                                        FunSpec.getterBuilder()
                                            .addStatement("return Companion")
                                            .build()
                                    )
                                    .build()
                            )
                            .addType(
                                TypeSpec.classBuilder("Impl")
                                    .addSuperinterface(thisCname)
                                    .apply {
                                        if (type.parent != null) {
                                            val parentVariableName = type.parent.variableName()
                                            val parentCname = type.parent.className()
                                            addProperty(
                                                PropertySpec.builder(parentVariableName, parentCname)
                                                    .addModifiers(KModifier.OVERRIDE)
                                                    .initializer(CodeBlock.of(parentVariableName))
                                                    .build()
                                            )
                                            addProperty(
                                                PropertySpec.builder("handle", CBasicType.int64_t.kotlinTypeName)
                                                    .addModifiers(KModifier.OVERRIDE)
                                                    .initializer(CodeBlock.of("handle"))
                                                    .build()
                                            )
                                            primaryConstructor(
                                                FunSpec.constructorBuilder()
                                                    .addParameter(parentVariableName, type.parent.className())
                                                    .addParameter("handle", CBasicType.int64_t.kotlinTypeName)
                                                    .build()
                                            )
                                            val grandparent = type.parent.parent
                                            if (grandparent != null) {
                                                addSuperinterface(
                                                    grandparent.className().nestedClass("Child"),
                                                    CodeBlock.of(parentVariableName)
                                                )
                                            }
                                        } else {
                                            addSuperclassConstructorParameter(
                                                CodeBlock.of("%M", CBasicType.int64_t.valueLayoutMember)
                                            )
                                            addProperty(
                                                PropertySpec.builder("handle", CBasicType.int64_t.kotlinTypeName)
                                                    .addModifiers(KModifier.OVERRIDE)
                                                    .initializer(CodeBlock.of("handle"))
                                                    .build()
                                            )
                                            primaryConstructor(
                                                FunSpec.constructorBuilder()
                                                    .addParameter("handle", CBasicType.int64_t.kotlinTypeName)
                                                    .build()
                                            )
                                        }
                                    }
                                    .build()
                            )
                            .addType(
                                TypeSpec.interfaceBuilder("Child")
                                    .apply {
                                        type.parent?.className()?.nestedClass("Child")?.let {
                                            addSuperinterface(it)
                                        }
                                    }
                                    .addProperty(type.variableName(), thisCname)
                                    .build()
                            )
                            .addType(
                                TypeSpec.companionObjectBuilder()
                                    .superclass(vkTypeDescriptorCname.parameterizedBy(thisCname))
                                    .addFunction(
                                        FunSpec.builder("toInt")
                                            .addAnnotation(JvmStatic::class)
                                            .addModifiers(KModifier.INLINE)
                                            .addParameter("value", thisCname)
                                            .returns(CBasicType.int64_t.kotlinTypeName)
                                            .addStatement("return value.handle")
                                            .build()
                                    )
                                    .addFunction(
                                        FunSpec.builder("fromInt")
                                            .addAnnotation(JvmStatic::class)
                                            .addModifiers(KModifier.INLINE)
                                            .addParameter("value", CBasicType.int64_t.kotlinTypeName)
                                            .returns(thisCname)
                                            .addStatement("throw %T()", UnsupportedOperationException::class)
                                            .build()
                                    )
                                    .addMethodHandleFields()
                                    .build()
                            )
                            .build()
                    )
            }
            .forEach(ctx::writeOutput)

        typeAlias.joinAndWriteOutput(VKFFI.handlePackageName)
    }
}
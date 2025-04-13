package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import net.echonolix.ktffi.CType
import net.echonolix.ktffi.KTFFICodegenHelper
import net.echonolix.ktffi.NativeType
import net.echonolix.ktffi.className
import net.echonolix.ktffi.decap

class GenerateHandleTask(ctx: VKFFICodeGenContext) : VKFFITask<Unit>(ctx) {
    fun CType.Handle.variableName(): String {
        return name.removePrefix("Vk").decap()
    }

    override fun VKFFICodeGenContext.compute() {
        val handles = ctx.filterType<CType.Handle>()
        val typeAlias = GenTypeAliasTask(this, handles).fork()
        val vkHandleCname = ClassName(VKFFI.handlePackageName, "VkHandle")
        val vkTypeDescriptorCname = vkHandleCname.nestedClass("TypeDescriptor")

        val objTypeCname = resolveType("VkObjectType").className()
        val typeVariable = TypeVariableName("T", vkHandleCname)
        val vkHandleFile = FileSpec.builder(vkHandleCname)
            .addType(
                TypeSpec.interfaceBuilder(vkHandleCname)
                    .addSuperinterface(NativeType::class)
                    .addProperty("objectType", objTypeCname)
                    .addType(
                        TypeSpec.classBuilder(vkTypeDescriptorCname)
                            .addModifiers(KModifier.ABSTRACT)
                            .addTypeVariable(typeVariable)
                            .superclass(KTFFICodegenHelper.typeDescriptorImplCname.parameterizedBy(typeVariable))
                            .addSuperclassConstructorParameter("%M", KTFFICodegenHelper.addressLayoutMember)
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
                val superType = type.parent?.className()?.nestedClass("Child") ?: vkHandleCname
                val thisTypeDescriptor = KTFFICodegenHelper.typeDescriptorCname.parameterizedBy(thisCname)
                FileSpec.builder(thisCname)
                    .addType(
                        TypeSpec.interfaceBuilder(thisCname)
                            .addSuperinterface(superType)
                            .addProperty(
                                PropertySpec.builder("objectType", objTypeCname)
                                    .addModifiers(KModifier.OVERRIDE)
                                    .getter(
                                        FunSpec.getterBuilder()
                                            .addStatement("return %M", type.objectTypeEnum.memberName())
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
                                            primaryConstructor(
                                                FunSpec.constructorBuilder()
                                                    .addParameter(parentVariableName, type.parent.className())
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
                                            superclass(KTFFICodegenHelper.typeImplCname.parameterizedBy(thisCname))
                                            addSuperclassConstructorParameter(
                                                CodeBlock.of(
                                                    "%M",
                                                    KTFFICodegenHelper.addressLayoutMember
                                                )
                                            )
                                        }
                                    }
                                    .addProperty(
                                        PropertySpec.builder("objectType", objTypeCname)
                                            .addModifiers(KModifier.OVERRIDE)
                                            .getter(
                                                FunSpec.getterBuilder()
                                                    .addStatement("return super<%T>.objectType", thisCname)
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .addProperty(
                                        PropertySpec.builder("typeDescriptor", thisTypeDescriptor)
                                            .addModifiers(KModifier.OVERRIDE)
                                            .getter(
                                                FunSpec.getterBuilder()
                                                    .addStatement("return super<%T>.typeDescriptor", thisCname)
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .build()
                            )
                            .addType(
                                TypeSpec.interfaceBuilder("Child")
                                    .addSuperinterface(superType)
                                    .addProperty(type.variableName(), thisCname)
                                    .build()
                            )
                            .addType(
                                TypeSpec.companionObjectBuilder()
                                    .superclass(vkTypeDescriptorCname.parameterizedBy(thisCname))
                                    .build()
                            )
                            .build()
                    )
            }
            .forEach(ctx::writeOutput)

        typeAlias.joinAndWriteOutput(VKFFI.handlePackageName)
    }
}
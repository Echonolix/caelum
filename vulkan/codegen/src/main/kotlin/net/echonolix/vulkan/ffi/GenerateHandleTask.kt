package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import net.echonolix.ktffi.CType
import net.echonolix.ktffi.KTFFICodegenHelper
import net.echonolix.ktffi.NativeType
import net.echonolix.ktffi.className
import net.echonolix.ktffi.decap
import net.echonolix.ktffi.memberName

class GenerateHandleTask(ctx: VKFFICodeGenContext) : VKFFITask<Unit>(ctx) {
    fun CType.Handle.variableName(): String {
        return name.removePrefix("Vk").decap()
    }

    override fun VKFFICodeGenContext.compute() {
        val handles = ctx.filterType<CType.Handle>()
        val typeAlias = GenTypeAliasTask(this, handles).fork()
        val vkHandleCname = ClassName(ctx.handlePackageName, "VkHandle")

        val objTypeCname = resolveType("VkObjectType").className()
        val vkHandleFile = FileSpec.builder(vkHandleCname)
            .addType(
                TypeSpec.interfaceBuilder(vkHandleCname)
                    .addSuperinterface(NativeType::class)
                    .addProperty("objectType", objTypeCname)
                    .build()
            )
        ctx.writeOutput(vkHandleFile)

        handles.parallelStream()
            .filter { (name, dstType) -> name == dstType.name }
            .map { (_, type) ->
                type as VkHandle
                val cname = type.className()
                val superType = type.parent?.className()?.nestedClass("Child") ?: vkHandleCname
                FileSpec.builder(cname)
                    .addType(
                        TypeSpec.interfaceBuilder(cname)
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
                            .addType(
                                TypeSpec.classBuilder("Impl")
                                    .addSuperinterface(cname)
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
                                            superclass(KTFFICodegenHelper.typeImplCname)
                                            addSuperclassConstructorParameter(CodeBlock.of("%M", KTFFICodegenHelper.addressLayoutMember))
                                        }
                                    }
                                    .addProperty(
                                        PropertySpec.builder("objectType", objTypeCname)
                                            .addModifiers(KModifier.OVERRIDE)
                                            .getter(
                                                FunSpec.getterBuilder()
                                                    .addStatement("return super<%T>.objectType", cname)
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .build()
                            )
                            .addType(
                                TypeSpec.interfaceBuilder("Child")
                                    .addSuperinterface(superType)
                                    .addProperty(type.variableName(), cname)
                                    .build()
                            )
                            .build()
                    )
            }
            .forEach(ctx::writeOutput)

        typeAlias.joinAndWriteOutput(ctx.handlePackageName)
    }
}
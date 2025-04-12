package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import net.echonolix.ktffi.CType
import net.echonolix.ktffi.NativeType
import net.echonolix.ktffi.className
import net.echonolix.ktffi.decap
import net.echonolix.ktffi.memberName

class GenerateHandleTask(ctx: VKFFICodeGenContext) : VKFFITask<Unit>(ctx) {
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
                                TypeSpec.interfaceBuilder("Child")
                                    .addSuperinterface(superType)
                                    .addProperty(type.name.removePrefix("Vk").decap(), cname)
                                    .build()
                            )
                            .build()
                    )
            }
            .forEach(ctx::writeOutput)

        typeAlias.joinAndWriteOutput(ctx.handlePackageName)
    }
}
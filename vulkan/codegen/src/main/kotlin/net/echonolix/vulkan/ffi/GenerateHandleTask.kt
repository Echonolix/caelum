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
import net.echonolix.ktffi.memberName
import java.util.Properties
import java.util.concurrent.RecursiveAction

class GenerateHandleTask(private val ctx: VKFFICodeGenContext) : RecursiveAction() {
    override fun compute() {
        ctx.compute()
    }

    private fun VKFFICodeGenContext.compute() {
        val typeAlias = GenTypeAliasTask(this, ctx.allHandles).fork()
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

        ctx.allTypes.entries.parallelStream()
            .filter { it.value is CType.Handle }
            .filter { it.key == it.value.name }
            .map { (_, type) ->
                type as VkHandle
                val cname = type.className()
                FileSpec.builder(cname)
                    .addType(
                        TypeSpec.interfaceBuilder(cname)
                            .addSuperinterface(type.parent?.className() ?: vkHandleCname)
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
                            .build()
                    )
            }
            .forEach { ctx.writeOutput(it) }

        val typeAliasesFile = FileSpec.builder(ctx.handlePackageName, "TypeAliases")
        typeAlias.join().forEach {
            typeAliasesFile.addTypeAlias(it)
        }
        ctx.writeOutput(typeAliasesFile)
    }
}